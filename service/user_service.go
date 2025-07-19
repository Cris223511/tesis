package services

import (
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"log"
	"math/big"
	"net"
	"net/smtp"
	"os"
	"strings"
	"sync"
	"time"
	"unicode"

	"usuarios/models"

	"regexp"

	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"
)




type UserService interface {
	CreateUser(user *models.User) (tempPassword string, err error)
	GetUserByID(id uint) (*models.User, error)
	UpdateUser(id uint, updates map[string]interface{}) error
	DeleteUser(id uint) error
	LoginUser(username, password, clientIP, userAgent string) (*models.User, error)
	ListUsers() ([]models.User, error)
	SearchUserByField(field, value string) ([]models.User, error)
	CheckUserExists(field, value string) (bool, error)
	UpdateUserPassword(userID uint, newPassword string) error
	SetAccountStatus(id uint, status bool) error
	UnlockAccount(userID uint, adminID uint) error
	GetLoginAttempts(userID uint) (int, time.Time, error)
	DB() *gorm.DB
}

type userService struct {
	db              *gorm.DB
	loginAttempts   *sync.Map
	ipRateLimiter   *IPRateLimiter
	securityMonitor *SecurityMonitor
}

type loginAttempt struct {
	count     int
	lastTry   time.Time
	lockedAt  *time.Time
	failedIPs []string
}

type IPRateLimiter struct {
	mu       sync.RWMutex
	attempts map[string][]time.Time
}

type SecurityMonitor struct {
	mu              sync.RWMutex
	suspiciousIPs   map[string]int
	blockedIPs      map[string]time.Time
	anomalyPatterns []string
}

func NewUserService(db *gorm.DB) UserService {
	return &userService{
		db:              db,
		loginAttempts:   &sync.Map{},
		ipRateLimiter:   NewIPRateLimiter(),
		securityMonitor: NewSecurityMonitor(),
	}
}

func NewIPRateLimiter() *IPRateLimiter {
	return &IPRateLimiter{
		attempts: make(map[string][]time.Time),
	}
}

func NewSecurityMonitor() *SecurityMonitor {
	return &SecurityMonitor{
		suspiciousIPs: make(map[string]int),
		blockedIPs:    make(map[string]time.Time),
		anomalyPatterns: []string{
			"admin", "root", "test", "' OR '1'='1", 
			"DROP TABLE", "SELECT * FROM", "UNION SELECT",
		},
	}
}

func (s *userService) LoginUser(username, password, clientIP, userAgent string) (*models.User, error) {
	normalizedIP := normalizeIP(clientIP)
	
	if err := s.checkIPRateLimit(normalizedIP); err != nil {
		s.logSecurityEvent("LOGIN_RATE_LIMIT", username, normalizedIP, "IP bloqueada por múltiples intentos")
		return nil, err
	}

	if s.isIPBlocked(normalizedIP) {
		s.logSecurityEvent("LOGIN_BLOCKED_IP", username, normalizedIP, "Intento desde IP bloqueada")
		return nil, errors.New("acceso denegado desde esta ubicación")
	}

	if s.detectSQLInjection(username) || s.detectSQLInjection(password) {
		s.blockIP(normalizedIP, 24*time.Hour)
		s.logSecurityEvent("SQL_INJECTION_ATTEMPT", username, normalizedIP, "Posible intento de SQL injection")
		return nil, errors.New("solicitud inválida")
	}

	var user models.User
	err := s.db.Preload("Roles").Preload("BiometricCreds").
		Where("(nombre_usuario = ? OR correo = ? OR numero_documento = ?) AND activo = ?",
			username, username, username, true).
		First(&user).Error

	if err != nil {
		s.recordFailedAttempt(username, normalizedIP)
		if errors.Is(err, gorm.ErrRecordNotFound) {
			time.Sleep(randomDelay())
			return nil, errors.New("credenciales inválidas")
		}
		return nil, errors.New("error al verificar usuario")
	}

	if err := s.checkAccountLockStatus(&user); err != nil {
		s.logSecurityEvent("LOGIN_LOCKED_ACCOUNT", username, normalizedIP, "Intento en cuenta bloqueada")
		return nil, err
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.Contrasena), []byte(password)); err != nil {
		s.handleFailedLogin(&user, normalizedIP)
		time.Sleep(randomDelay())
		return nil, errors.New("credenciales inválidas")
	}

	if user.PasswordExpiresAt.Before(time.Now()) && !user.PasswordExpiresAt.IsZero() {
		s.logSecurityEvent("LOGIN_EXPIRED_PASSWORD", username, normalizedIP, "Contraseña expirada")
		return nil, errors.New("contraseña expirada, debe actualizarla")
	}

	if err := s.detectAnomalousLogin(&user, normalizedIP, userAgent); err != nil {
		s.sendSecurityAlert(&user, normalizedIP, "Inicio de sesión desde ubicación inusual")
	}

	s.recordSuccessfulLogin(&user, normalizedIP, userAgent)
	
	return &user, nil
}

func (s *userService) checkIPRateLimit(ip string) error {
	s.ipRateLimiter.mu.Lock()
	defer s.ipRateLimiter.mu.Unlock()

	now := time.Now()
	attempts := s.ipRateLimiter.attempts[ip]
	
	var validAttempts []time.Time
	for _, t := range attempts {
		if now.Sub(t) < 15*time.Minute {
			validAttempts = append(validAttempts, t)
		}
	}

	if len(validAttempts) >= 5 {
		return errors.New("demasiados intentos, intente más tarde")
	}

	validAttempts = append(validAttempts, now)
	s.ipRateLimiter.attempts[ip] = validAttempts
	
	return nil
}

func (s *userService) checkAccountLockStatus(user *models.User) error {
	if !user.Activo {
		return errors.New("cuenta desactivada")
	}

	key := fmt.Sprintf("user_%d", user.ID)
	if val, ok := s.loginAttempts.Load(key); ok {
		attempt := val.(*loginAttempt)
		if attempt.lockedAt != nil && time.Since(*attempt.lockedAt) < 30*time.Minute {
			remainingTime := 30*time.Minute - time.Since(*attempt.lockedAt)
			return fmt.Errorf("cuenta bloqueada, intente en %d minutos", int(remainingTime.Minutes()))
		}
	}

	return nil
}

func (s *userService) handleFailedLogin(user *models.User, ip string) {
	key := fmt.Sprintf("user_%d", user.ID)
	val, _ := s.loginAttempts.LoadOrStore(key, &loginAttempt{})
	attempt := val.(*loginAttempt)

	attempt.count++
	attempt.lastTry = time.Now()
	attempt.failedIPs = append(attempt.failedIPs, ip)

	if len(attempt.failedIPs) > 10 {
		attempt.failedIPs = attempt.failedIPs[len(attempt.failedIPs)-10:]
	}

	user.Intentos = attempt.count
	s.db.Model(user).Update("intentos", attempt.count)

	if attempt.count >= 3 {
		now := time.Now()
		attempt.lockedAt = &now
		user.Activo = false
		s.db.Model(user).Update("activo", false)
		
		s.logSecurityEvent("ACCOUNT_LOCKED", user.NombreUsuario, ip, 
			fmt.Sprintf("Cuenta bloqueada después de %d intentos", attempt.count))
		
		go s.sendAccountLockNotification(user.Correo, user.Nombre_Apellidos, ip)
	}

	s.loginAttempts.Store(key, attempt)
}

func (s *userService) recordSuccessfulLogin(user *models.User, ip, userAgent string) {
	key := fmt.Sprintf("user_%d", user.ID)
	s.loginAttempts.Delete(key)

	user.Intentos = 0
	now := time.Now()
	updates := map[string]interface{}{
		"intentos":       0,
		"last_login_at":  now,
		"last_login_ip":  ip,
		"last_user_agent": userAgent,
	}
	s.db.Model(user).Updates(updates)

	s.recordDeviceIP(user.ID, ip, userAgent)
	s.logSecurityEvent("LOGIN_SUCCESS", user.NombreUsuario, ip, "Login exitoso")
}

func (s *userService) detectAnomalousLogin(user *models.User, ip, userAgent string) error {
	var knownDevices []models.UserDeviceIP
	s.db.Where("user_id = ? AND is_trusted = ?", user.ID, true).Find(&knownDevices)

	for _, device := range knownDevices {
		if device.IP == ip {
			return nil
		}
	}

	var lastLogin models.LoginHistory
	s.db.Where("user_id = ?", user.ID).Order("created_at DESC").First(&lastLogin)

	if !lastLogin.CreatedAt.IsZero() {
		if lastLogin.IPAddress != ip {
			distance := calculateIPDistance(lastLogin.IPAddress, ip)
			timeDiff := time.Since(lastLogin.CreatedAt)
			
			if distance > 1000 && timeDiff < 1*time.Hour {
				return errors.New("ubicación sospechosa detectada")
			}
		}
	}

	return nil
}

func (s *userService) CreateUser(user *models.User) (string, error) {
	if err := s.validateUserData(user); err != nil {
		return "", err
	}

	tx := s.db.Begin()
	defer tx.Rollback()

	if err := s.checkDuplicateUser(tx, user); err != nil {
		return "", err
	}

	if err := s.validateRoles(tx, user); err != nil {
		return "", err
	}

	tempPassword := s.generateSecurePassword()
	hashedPassword, _ := bcrypt.GenerateFromPassword([]byte(tempPassword), bcrypt.DefaultCost)
	
	user.Contrasena = string(hashedPassword)
	user.PasswordExpiresAt = time.Now().Add(48 * time.Hour)
	user.Activo = false
	user.Intentos = 0
	user.NombreUsuario = s.generateUniqueUsername(user.Nombre_Apellidos)

	activationToken := s.generateActivationToken()
	user.ActivationToken = activationToken
	user.ActivationExpiry = time.Now().Add(24 * time.Hour)

	if err := tx.Create(user).Error; err != nil {
		return "", errors.New("error al crear usuario")
	}

	tx.Commit()
	
	go s.sendActivationEmail(user.Correo, tempPassword, user.NombreUsuario, activationToken)
	s.logSecurityEvent("USER_CREATED", user.NombreUsuario, "", "Usuario creado exitosamente")
	
	return tempPassword, nil
}

func (s *userService) validateUserData(user *models.User) error {
	if err := user.ValidateTipoDocumento(); err != nil {
		return err
	}

	if user.Tipo_Documento == models.TipoDocumentoDNI {
		if !s.isValidDNI(user.Numero_Documento) {
			return errors.New("DNI inválido")
		}
	}

	if !s.isValidEmail(user.Correo) {
		return errors.New("formato de correo inválido")
	}

	if err := s.validateNameQuality(user.Nombre_Apellidos); err != nil {
		return err
	}

	if user.FechaNacimiento.After(time.Now().AddDate(-1, 0, 0)) {
		return errors.New("debe ser mayor de 1 año")
	}

	if !s.isValidPhone(user.Celular) {
		return errors.New("número de celular inválido")
	}

	return nil
}

func(s *userService) validateRoles(tx *gorm.DB, user *models.User) error {
	if len(user.RoleIDs) == 0 {
		return errors.New("debe asignar al menos un rol")
	}

	for _, roleID := range user.RoleIDs {
		var role models.Role
		if err := tx.First(&role, roleID).Error; err != nil {
			return fmt.Errorf("rol con ID %d no encontrado", roleID)
		}
	}

	return nil
}

func (s *userService) validateNameQuality(fullName string) error {
	suspiciousPatterns := []string{
		"test", "prueba", "admin", "root", "user",
		"123", "aaa", "xxx", "asdf", "qwerty",
	}

	nameLower := strings.ToLower(fullName)
	for _, pattern := range suspiciousPatterns {
		if strings.Contains(nameLower, pattern) {
			return errors.New("nombre no válido")
		}
	}

	if len(strings.Fields(fullName)) < 2 {
		return errors.New("debe incluir nombre y apellido")
	}

	if matched, _ := regexp.MatchString(`^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s]+$`, fullName); !matched {
		return errors.New("el nombre solo debe contener letras")
	}

	return nil
}

func (s *userService) checkDuplicateUser(tx *gorm.DB, user *models.User) error {
	var count int64
	
	tx.Model(&models.User{}).Where("numero_documento = ? AND tipo_documento = ?", 
		user.Numero_Documento, user.Tipo_Documento).Count(&count)
	if count > 0 {
		return errors.New("ya existe un usuario con este documento")
	}

	tx.Model(&models.User{}).Where("correo = ?", user.Correo).Count(&count)
	if count > 0 {
		return errors.New("el correo ya está registrado")
	}

	fullName := strings.ToLower(strings.TrimSpace(user.Nombre_Apellidos))
	tx.Model(&models.User{}).Where("LOWER(TRIM(nombre_apellidos)) = ?", fullName).Count(&count)
	if count > 0 {
		s.logSecurityEvent("DUPLICATE_NAME_ATTEMPT", user.NombreUsuario, "", 
			"Intento de registro con nombre duplicado")
		return errors.New("ya existe un usuario con este nombre")
	}

	return nil
}

func (s *userService) UpdateUserPassword(userID uint, newPassword string) error {
	if err := s.validatePasswordStrength(newPassword); err != nil {
		return err
	}

	tx := s.db.Begin()
	defer tx.Rollback()

	var user models.User
	if err := tx.First(&user, userID).Error; err != nil {
		return errors.New("usuario no encontrado")
	}

	var passwordHistory []models.PasswordHistory
	tx.Where("user_id = ?", userID).Order("created_at DESC").Limit(5).Find(&passwordHistory)
	
	for _, history := range passwordHistory {
		if bcrypt.CompareHashAndPassword([]byte(history.PasswordHash), []byte(newPassword)) == nil {
			return errors.New("no puede reutilizar contraseñas anteriores")
		}
	}

	hashedPassword, _ := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	
	tx.Create(&models.PasswordHistory{
		UserID:       userID,
		PasswordHash: string(hashedPassword),
	})

	updates := map[string]interface{}{
		"contrasena":          string(hashedPassword),
		"password_expires_at": time.Now().AddDate(0, 6, 0),
		"password_changed_at": time.Now(),
	}
	
	if err := tx.Model(&user).Updates(updates).Error; err != nil {
		return errors.New("error al actualizar contraseña")
	}

	tx.Commit()
	
	clientIP := "Sistema"
	go s.sendPasswordUpdateNotification(user.Correo, user.Nombre_Apellidos, clientIP)
	s.logSecurityEvent("PASSWORD_CHANGED", user.NombreUsuario, "", "Contraseña actualizada")
	
	return nil
}

func (s *userService) validatePasswordStrength(password string) error {
	if len(password) < 12 {
		return errors.New("la contraseña debe tener al menos 12 caracteres")
	}

	var hasUpper, hasLower, hasNumber, hasSpecial bool
	for _, ch := range password {
		switch {
		case unicode.IsUpper(ch):
			hasUpper = true
		case unicode.IsLower(ch):
			hasLower = true
		case unicode.IsDigit(ch):
			hasNumber = true
		case strings.ContainsRune("!@#$%^&*()_+-=[]{}|;:,.<>?", ch):
			hasSpecial = true
		}
	}

	if !hasUpper || !hasLower || !hasNumber || !hasSpecial {
		return errors.New("la contraseña debe contener mayúsculas, minúsculas, números y caracteres especiales")
	}

	commonPasswords := []string{
		"password", "123456", "admin", "qwerty", "letmein",
		"welcome", "monkey", "dragon", "master", "hello",
	}
	
	passwordLower := strings.ToLower(password)
	for _, common := range commonPasswords {
		if strings.Contains(passwordLower, common) {
			return errors.New("contraseña muy común, elija otra")
		}
	}

	return nil
}

func (s *userService) ListUsers() ([]models.User, error) {
	var users []models.User
	err := s.db.Preload("Roles").Find(&users).Error
	return users, err
}

func (s *userService) GetUserByID(id uint) (*models.User, error) {
	var user models.User
	err := s.db.Preload("Roles").First(&user, id).Error
	if err != nil {
		return nil, errors.New("usuario no encontrado")
	}
	return &user, nil
}

func (s *userService) UpdateUser(id uint, updates map[string]interface{}) error {
	var user models.User
	if err := s.db.First(&user, id).Error; err != nil {
		return errors.New("usuario no encontrado")
	}

	allowedFields := map[string]bool{
		"celular": true,
		"correo": true,
		"foto": true,
	}

	filteredUpdates := make(map[string]interface{})
	var updatedFields []string
	
	for key, value := range updates {
		if allowedFields[key] {
			filteredUpdates[key] = value
			updatedFields = append(updatedFields, key)
		}
	}

	if correo, ok := filteredUpdates["correo"]; ok {
		if !s.isValidEmail(correo.(string)) {
			return errors.New("formato de correo inválido")
		}
	}

	err := s.db.Model(&user).Updates(filteredUpdates).Error
	if err != nil {
		return errors.New("error al actualizar usuario")
	}

	go s.sendProfileUpdateNotification(user.Correo, user.Nombre_Apellidos, updatedFields, "")
	s.logSecurityEvent("USER_UPDATED", fmt.Sprintf("user_id_%d", id), "", 
		fmt.Sprintf("Campos actualizados: %v", filteredUpdates))
	
	return nil
}

func (s *userService) DeleteUser(id uint) error {
	tx := s.db.Begin()
	defer tx.Rollback()

	var user models.User
	if err := tx.First(&user, id).Error; err != nil {
		return errors.New("usuario no encontrado")
	}

	tx.Where("user_id = ?", id).Delete(&models.Role{})
	tx.Where("user_id = ?", id).Delete(&models.BiometricCredential{})
	tx.Where("user_id = ?", id).Delete(&models.UserDeviceIP{})
	tx.Where("user_id = ?", id).Delete(&models.LoginHistory{})
	tx.Where("user_id = ?", id).Delete(&models.PasswordHistory{})

	if err := tx.Delete(&user).Error; err != nil {
		return errors.New("error al eliminar usuario")
	}

	tx.Commit()
	
	go s.sendAccountDeletionNotification(user.Correo, user.Nombre_Apellidos)
	s.logSecurityEvent("USER_DELETED", user.NombreUsuario, "", "Usuario eliminado")
	
	return nil
}

func (s *userService) SetAccountStatus(id uint, status bool) error {
	var user models.User
	if err := s.db.First(&user, id).Error; err != nil {
		return errors.New("usuario no encontrado")
	}

	user.Activo = status
	if err := s.db.Save(&user).Error; err != nil {
		return errors.New("error al actualizar estado")
	}

	action := "desactivada"
	emailFunc := s.sendAccountDeactivatedEmail
	if status {
		action = "activada"
		emailFunc = s.sendAccountActivatedEmail
	}
	
	go emailFunc(user.Correo, user.Nombre_Apellidos)
	s.logSecurityEvent("ACCOUNT_STATUS_CHANGED", user.NombreUsuario, "", 
		fmt.Sprintf("Cuenta %s", action))
	
	return nil
}

func (s *userService) UnlockAccount(userID uint, adminID uint) error {
	var user models.User
	if err := s.db.First(&user, userID).Error; err != nil {
		return errors.New("usuario no encontrado")
	}

	key := fmt.Sprintf("user_%d", userID)
	s.loginAttempts.Delete(key)

	updates := map[string]interface{}{
		"activo":   true,
		"intentos": 0,
	}
	
	if err := s.db.Model(&user).Updates(updates).Error; err != nil {
		return errors.New("error al desbloquear cuenta")
	}

	s.logSecurityEvent("ACCOUNT_UNLOCKED", user.NombreUsuario, "", 
		fmt.Sprintf("Desbloqueada por admin_id: %d", adminID))
	
	return nil
}

func (s *userService) GetLoginAttempts(userID uint) (int, time.Time, error) {
	key := fmt.Sprintf("user_%d", userID)
	if val, ok := s.loginAttempts.Load(key); ok {
		attempt := val.(*loginAttempt)
		return attempt.count, attempt.lastTry, nil
	}
	return 0, time.Time{}, nil
}

func (s *userService) SearchUserByField(field, value string) ([]models.User, error) {
	if s.detectSQLInjection(value) {
		return nil, errors.New("búsqueda inválida")
	}

	var users []models.User
	searchPattern := "%" + strings.ToLower(value) + "%"
	
	err := s.db.Preload("Roles").
		Where("LOWER(nombre_apellidos) LIKE ? OR LOWER(correo) LIKE ? OR numero_documento LIKE ?",
			searchPattern, searchPattern, searchPattern).
		Find(&users).Error
		
	return users, err
}

func (s *userService) CheckUserExists(field, value string) (bool, error) {
	var count int64
	err := s.db.Model(&models.User{}).Where(field+" = ?", value).Count(&count).Error
	return count > 0, err
}

func (s *userService) DB() *gorm.DB {
	return s.db
}

func (s *userService) generateSecurePassword() string {
	const (
		length  = 16
		upper   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
		lower   = "abcdefghijklmnopqrstuvwxyz"
		numbers = "0123456789"
		special = "!@#$%^&*"
	)

	password := make([]byte, length)
	password[0] = randomChar(upper)
	password[1] = randomChar(lower)
	password[2] = randomChar(numbers)
	password[3] = randomChar(special)

	allChars := upper + lower + numbers + special
	for i := 4; i < length; i++ {
		password[i] = randomChar(allChars)
	}

	shuffle(password)
	return string(password)
}

func (s *userService) generateUniqueUsername(fullName string) string {
	words := strings.Fields(strings.ToLower(fullName))
	if len(words) < 2 {
		words = append(words, "user")
	}
	
	baseUsername := fmt.Sprintf("%s.%s", words[0], words[len(words)-1])
	username := baseUsername
	counter := 1

	for {
		var count int64
		s.db.Model(&models.User{}).Where("nombre_usuario = ?", username).Count(&count)
		if count == 0 {
			break
		}
		username = fmt.Sprintf("%s%d", baseUsername, counter)
		counter++
	}

	return username
}

func (s *userService) generateActivationToken() string {
	b := make([]byte, 32)
	rand.Read(b)
	return base64.URLEncoding.EncodeToString(b)
}

func (s *userService) recordDeviceIP(userID uint, ip, userAgent string) {
	device := models.UserDeviceIP{
		UserID:    userID,
		IP:        ip,
		Device:    userAgent,
		CreatedAt: time.Now(),

	}
	s.db.Create(&device)
}

func (s *userService) detectSQLInjection(input string) bool {
	sqlPatterns := []string{
		"' OR '", "' AND '", "DROP TABLE", "SELECT * FROM",
		"UNION SELECT", "INSERT INTO", "DELETE FROM",
		"UPDATE SET", "--", "/*", "*/", "xp_", "sp_",
	}
	
	inputLower := strings.ToLower(input)
	for _, pattern := range sqlPatterns {
		if strings.Contains(inputLower, strings.ToLower(pattern)) {
			return true
		}
	}
	
	return false
}

func (s *userService) isIPBlocked(ip string) bool {
	s.securityMonitor.mu.RLock()
	defer s.securityMonitor.mu.RUnlock()
	
	if blockedUntil, exists := s.securityMonitor.blockedIPs[ip]; exists {
		return time.Now().Before(blockedUntil)
	}
	return false
}

func (s *userService) blockIP(ip string, duration time.Duration) {
	s.securityMonitor.mu.Lock()
	defer s.securityMonitor.mu.Unlock()
	
	s.securityMonitor.blockedIPs[ip] = time.Now().Add(duration)
}

func (s *userService) recordFailedAttempt(username, ip string) {
	s.securityMonitor.mu.Lock()
	defer s.securityMonitor.mu.Unlock()
	
	s.securityMonitor.suspiciousIPs[ip]++
	
	if s.securityMonitor.suspiciousIPs[ip] > 10 {
		s.blockIP(ip, 24*time.Hour)
	}
}

func (s *userService) logSecurityEvent(eventType, username, ip, details string) {
	event := models.SecurityLog{
		EventType: eventType,
		Username:  username,
		IPAddress: ip,
		Details:   details,
		CreatedAt: time.Now(),
	}
	s.db.Create(&event)
	
	if eventType == "SQL_INJECTION_ATTEMPT" || eventType == "ACCOUNT_LOCKED" {
		log.Printf("[SECURITY ALERT] %s - User: %s, IP: %s, Details: %s", 
			eventType, username, ip, details)
	}
}

func (s *userService) isValidDNI(dni string) bool {
	if len(dni) != 8 {
		return false
	}
	
	if matched, _ := regexp.MatchString(`^\d{8}$`, dni); !matched {
		return false
	}
	
	invalidDNIs := []string{"00000000", "11111111", "12345678", "87654321"}
	for _, invalid := range invalidDNIs {
		if dni == invalid {
			return false
		}
	}
	
	return true
}

func (s *userService) isValidEmail(email string) bool {
	emailRegex := `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$`
	matched, _ := regexp.MatchString(emailRegex, email)
	return matched
}

func (s *userService) isValidPhone(phone string) bool {
	if len(phone) != 9 {
		return false
	}
	
	matched, _ := regexp.MatchString(`^9\d{8}$`, phone)
	return matched
}

func (s *userService) sendActivationEmail(email, tempPassword, username, token string) {
	activationURL := fmt.Sprintf("https://portal.untumbes.edu.pe/activate?token=%s", token)
	subject := "Activa tu Cuenta - Serious Game"
	
	body := fmt.Sprintf(`
	<html>
	<body style="font-family: Arial, sans-serif;">
		<h2>Bienvenido a Serious Game</h2>
		<p>Hola %s,</p>
		<p>Tu cuenta ha sido creada exitosamente.</p>
		<p><strong>Usuario:</strong> %s<br>
		<strong>Contraseña temporal:</strong> %s</p>
		<p>Activa tu cuenta aquí: <a href="%s">Activar cuenta</a></p>
		<p>Este enlace expira en 24 horas.</p>
	</body>
	</html>`, username, username, tempPassword, activationURL)
	
	sendEmail(email, subject, body)
}

func (s *userService) sendAccountLockNotification(email, name, ip string) {
	subject := "Cuenta Bloqueada - Serious Game"
	body := fmt.Sprintf(`
	<html>
	<body>
		<h2>Cuenta Bloqueada por Seguridad</h2>
		<p>Estimado(a) %s,</p>
		<p>Tu cuenta ha sido bloqueada después de múltiples intentos fallidos desde la IP: %s</p>
		<p>Si no fuiste tú, contacta inmediatamente con soporte.</p>
		<p>Para desbloquear tu cuenta, contacta al administrador.</p>
	</body>
	</html>`, name, ip)
	
	sendEmail(email, subject, body)
}

func (s *userService) sendPasswordUpdateNotification(email, name, ip string) {
	subject := "Actualización de Contraseña - Serious Game"
	body := fmt.Sprintf(`
	<html>
	<head><meta charset="UTF-8"/></head>
	<body style="font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px;">
		<div style="max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
			<div style="background-color: #004165; color: #fff; padding: 20px; text-align: center;">
				<h1 style="margin: 0;">Serious Game</h1>
			</div>
			<div style="padding: 30px;">
				<h2 style="color: #004165;">Contraseña Actualizada</h2>
				<p>Estimado(a) <strong>%s</strong>,</p>
				<p>Tu contraseña ha sido actualizada correctamente.</p>
				<div style="background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin: 20px 0;">
					<p style="margin: 5px 0;"><strong>Fecha:</strong> %s</p>
					<p style="margin: 5px 0;"><strong>IP:</strong> %s</p>
				</div>
				<p style="color: #d32f2f; font-weight: bold;">Si no realizaste este cambio, contacta inmediatamente con soporte.</p>
			</div>
			<div style="background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666;">
				© %d Soporte Serious Game- Todos los derechos reservados
			</div>
		</div>
	</body>
	</html>`, name, time.Now().Format("02/01/2006 15:04:05"), ip, time.Now().Year())
	
	sendEmail(email, subject, body)
}

func (s *userService) sendProfileUpdateNotification(email, name string, fields []string, ip string) {
	subject := "Actualización de Perfil - Serious Game"
	
	fieldsHTML := ""
	fieldTranslations := map[string]string{
		"celular": "Número de celular",
		"correo": "Correo electrónico",
		"foto": "Foto de perfil",
	}
	
	for _, field := range fields {
		fieldName := field
		if translated, exists := fieldTranslations[field]; exists {
			fieldName = translated
		}
		fieldsHTML += fmt.Sprintf("<li>%s</li>", fieldName)
	}
	
	body := fmt.Sprintf(`
	<html>
	<head><meta charset="UTF-8"/></head>
	<body style="font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px;">
		<div style="max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
			<div style="background-color: #004165; color: #fff; padding: 20px; text-align: center;">
				<h1 style="margin: 0;">Serious Game</h1>
			</div>
			<div style="padding: 30px;">
				<h2 style="color: #004165;">Actualización de Perfil</h2>
				<p>Hola <strong>%s</strong>,</p>
				<p>Los siguientes datos de tu perfil han sido actualizados:</p>
				<ul style="background-color: #f8f9fa; padding: 15px 15px 15px 35px; border-radius: 5px;">
					%s
				</ul>
				<p><strong>Fecha:</strong> %s</p>
				<p style="color: #d32f2f;">Si no realizaste estos cambios, contacta con soporte inmediatamente.</p>
			</div>
			<div style="background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666;">
				© %d Soporte Serious Game - Todos los derechos reservados
			</div>
		</div>
	</body>
	</html>`, name, fieldsHTML, time.Now().Format("02/01/2006 15:04:05"), time.Now().Year())
	
	sendEmail(email, subject, body)
}

func (s *userService) sendAccountActivatedEmail(email, name string) {
	subject := "Cuenta Activada - Soporte Serious Game"
	body := fmt.Sprintf(`
	<html>
	<head><meta charset="UTF-8"/></head>
	<body style="font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px;">
		<div style="max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
			<div style="background-color: #4CAF50; color: #fff; padding: 20px; text-align: center;">
				<h1 style="margin: 0;">¡Cuenta Activada!</h1>
			</div>
			<div style="padding: 30px;">
				<h2 style="color: #004165;">Bienvenido(a) %s</h2>
				<p>Tu cuenta ha sido activada exitosamente.</p>
				<p>Ahora puedes acceder al portal con tus credenciales.</p>
				<div style="text-align: center; margin: 30px 0;">
					<a href="https://portal" style="background-color: #004165; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">Ir al Portal</a>
				</div>
			</div>
			<div style="background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666;">
				© %d Serious Game - Todos los derechos reservados
			</div>
		</div>
	</body>
	</html>`, name, time.Now().Year())
	
	sendEmail(email, subject, body)
}

func (s *userService) sendAccountDeactivatedEmail(email, name string) {
	subject := "Cuenta Desactivada - Serious Game"
	body := fmt.Sprintf(`
	<html>
	<head><meta charset="UTF-8"/></head>
	<body style="font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px;">
		<div style="max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
			<div style="background-color: #f44336; color: #fff; padding: 20px; text-align: center;">
				<h1 style="margin: 0;">Cuenta Desactivada</h1>
			</div>
			<div style="padding: 30px;">
				<h2 style="color: #f44336;">Importante</h2>
				<p>Estimado(a) <strong>%s</strong>,</p>
				<p>Tu cuenta ha sido desactivada por motivos de seguridad o administrativos.</p>
				<p>Si consideras que esto es un error o deseas más información, por favor contacta con el administrador.</p>
				<div style="background-color: #fff3cd; border: 1px solid #ffeaa7; padding: 15px; border-radius: 5px; margin: 20px 0;">
					<p style="margin: 0; color: #856404;"><strong>Nota:</strong> No podrás acceder al portal hasta que tu cuenta sea reactivada.</p>
				</div>
			</div>
			<div style="background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666;">
				© %d Soporte Serious Game- Todos los derechos reservados
			</div>
		</div>
	</body>
	</html>`, name, time.Now().Year())
	
	sendEmail(email, subject, body)
}

func (s *userService) sendAccountDeletionNotification(email, name string) {
	subject := "Cuenta Eliminada - Serious Game"
	body := fmt.Sprintf(`
	<html>
	<head><meta charset="UTF-8"/></head>
	<body style="font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px;">
		<div style="max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
			<div style="background-color: #9E9E9E; color: #fff; padding: 20px; text-align: center;">
				<h1 style="margin: 0;">Cuenta Eliminada</h1>
			</div>
			<div style="padding: 30px;">
				<p>Estimado(a) <strong>%s</strong>,</p>
				<p>Te informamos que tu cuenta ha sido eliminada permanentemente del sistema.</p>
				<p>Todos tus datos han sido removidos de acuerdo con nuestras políticas de privacidad.</p>
				<div style="background-color: #e3f2fd; border: 1px solid #90caf9; padding: 15px; border-radius: 5px; margin: 20px 0;">
					<p style="margin: 0; color: #1565c0;">Si deseas crear una nueva cuenta en el futuro, deberás realizar un nuevo registro.</p>
				</div>
				<p>Lamentamos verte partir. Si tienes alguna pregunta, no dudes en contactarnos.</p>
			</div>
			<div style="background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666;">
				© %d Soporte Serious Game - Todos los derechos reservados
			</div>
		</div>
	</body>
	</html>`, name, time.Now().Year())
	
	sendEmail(email, subject, body)
}

func (s *userService) sendSecurityAlert(user *models.User, ip, reason string) {
	subject := "Alerta de Seguridad - Serious Game"
	body := fmt.Sprintf(`
	<html>
	<body>
		<h2>Alerta de Seguridad</h2>
		<p>Hola %s,</p>
		<p>Detectamos actividad inusual en tu cuenta:</p>
		<p><strong>Razón:</strong> %s<br>
		<strong>IP:</strong> %s<br>
		<strong>Fecha:</strong> %s</p>
		<p>Si no fuiste tú, cambia tu contraseña inmediatamente.</p>
	</body>
	</html>`, user.Nombre_Apellidos, reason, ip, time.Now().Format("02/01/2006 15:04"))
	
	sendEmail(user.Correo, subject, body)
}

func sendEmail(toEmail, subject, bodyHTML string) error {
	smtpHost := os.Getenv("SMTP_HOST")
	smtpPort := os.Getenv("SMTP_PORT")
	senderEmail := os.Getenv("SMTP_EMAIL")
	senderPassword := os.Getenv("SMTP_PASSWORD")

	if smtpHost == "" {
		smtpHost = "smtp.gmail.com"
		smtpPort = "587"
	}

	auth := smtp.PlainAuth("", senderEmail, senderPassword, smtpHost)

	msg := []byte("From: " + senderEmail + "\r\n" +
		"To: " + toEmail + "\r\n" +
		"Subject: " + subject + "\r\n" +
		"MIME-Version: 1.0\r\n" +
		"Content-Type: text/html; charset=UTF-8\r\n\r\n" +
		bodyHTML)

	err := smtp.SendMail(smtpHost+":"+smtpPort, auth, senderEmail, []string{toEmail}, msg)
	if err != nil {
		log.Printf("[ERROR] No se pudo enviar email a %s: %v", toEmail, err)
		return err
	}
	
	return nil
}

func randomChar(charset string) byte {
	idx, _ := rand.Int(rand.Reader, big.NewInt(int64(len(charset))))
	return charset[idx.Int64()]
}

func shuffle(data []byte) {
	n := len(data)
	for i := n - 1; i > 0; i-- {
		j, _ := rand.Int(rand.Reader, big.NewInt(int64(i+1)))
		data[i], data[j.Int64()] = data[j.Int64()], data[i]
	}
}

func randomDelay() time.Duration {
	delay, _ := rand.Int(rand.Reader, big.NewInt(3000))
	return time.Duration(delay.Int64()+1000) * time.Millisecond
}

func normalizeIP(ip string) string {
	if strings.Contains(ip, ":") {
		host, _, _ := net.SplitHostPort(ip)
		return host
	}
	return ip
}

func calculateIPDistance(ip1, ip2 string) float64 {
	return 0
}