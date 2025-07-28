package services

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
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
	CreateUser(user *models.Usuarios) (tempPassword string, err error)
	GetUserByID(id uint) (*models.Usuarios, error)
	UpdateUser(id uint, updates map[string]interface{}) error
	DeleteUser(id uint) error
	LoginUser(username, password, clientIP, userAgent string) (*models.Usuarios, error)
	ListUsers() ([]models.Usuarios, error)
	SearchUserByField(field, value string) ([]models.Usuarios, error)
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

func (s *userService) LoginUser(username, password, clientIP, userAgent string) (*models.Usuarios, error) {
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

	var user models.Usuarios
	err := s.db.Preload("Roles").Preload("BiometricCreds").
		Where("(usuario = ? OR correo = ? OR num_documento = ?) AND activo = ?",
			username, username, username, false).
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

func (s *userService) checkAccountLockStatus(user *models.Usuarios) error {
	if user.Activo {  
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
func (s *userService) handleFailedLogin(user *models.Usuarios, ip string) {
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
		
		s.logSecurityEvent("ACCOUNT_LOCKED", user.Usuario, ip, 
			fmt.Sprintf("Cuenta bloqueada temporalmente después de %d intentos", attempt.count))
		
		go s.sendAccountLockNotification(user.Correo, user.Nombres_Apellidos, ip)
	}

	s.loginAttempts.Store(key, attempt)
}

func (s *userService) recordSuccessfulLogin(user *models.Usuarios, ip, userAgent string) {
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
	s.logSecurityEvent("LOGIN_SUCCESS", user.Usuario, ip, "Login exitoso")
}

func (s *userService) detectAnomalousLogin(user *models.Usuarios, ip, userAgent string) error {
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

func (s *userService) CreateUser(user *models.Usuarios) (string, error) {
	log.Printf("CreateUser recibido: Nombres_Apellidos='%s'", user.Nombres_Apellidos)
    log.Printf("Usuario completo en CreateUser: %+v", user)
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
	user.DobleFactor = false
	user.Intentos = 0
	user.Usuario = s.generateUniqueUsername(user.Nombres_Apellidos)

	activationToken := s.generateActivationToken()
	user.ActivationToken = activationToken
	user.ActivationExpiry = time.Now().Add(24 * time.Hour)

	log.Printf("Usuario ANTES de tx.Create: Nombres_Apellidos='%s'", user.Nombres_Apellidos)
log.Printf("Usuario completo antes de insert: %+v", user)

if err := tx.Select("nombres_apellidos", "fecha_nacimiento", "tipo_documento", 
    "num_documento", "sexo", "telefono", "correo", "usuario", 
    "contrasena", "intentos", "doble_factor", "activo", 
    "password_expires_at", "activation_token", "activation_expiry").Create(user).Error; err != nil {
    log.Printf("Error al insertar en BD: %v", err)
    return "", fmt.Errorf("error al crear usuario: %v", err) 
}


	tx.Commit()
	
	go s.sendActivationEmail(user.Correo, tempPassword, user.Usuario, activationToken)
	s.logSecurityEvent("USER_CREATED", user.Usuario, "", "Usuario creado exitosamente")
	
	return tempPassword, nil
}

func (s *userService) validateUserData(user *models.Usuarios) error {
	if err := user.ValidateTipoDocumento(); err != nil {
		return err
	}

	if user.Tipo_Documento == models.TipoDocumentoDNI {
		if !s.isValidDNI(user.Num_Documento) {
			return errors.New("DNI inválido")
		}
	}

	if !s.isValidEmail(user.Correo) {
		return errors.New("formato de correo inválido")
	}

	if err := s.validateNameQuality(user.Nombres_Apellidos); err != nil {
		return err
	}

	

	if !s.isValidPhone(user.Telefono) {
		return errors.New("número de celular inválido")
	}

	return nil
}

func(s *userService) validateRoles(tx *gorm.DB, user *models.Usuarios) error {
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

func (s *userService) checkDuplicateUser(tx *gorm.DB, user *models.Usuarios) error {
	var count int64
	
	tx.Model(&models.Usuarios{}).Where("num_documento = ? AND tipo_documento = ?", 
		user.Num_Documento, user.Tipo_Documento).Count(&count)
	if count > 0 {
		return errors.New("ya existe un usuario con este documento")
	}

	tx.Model(&models.Usuarios{}).Where("correo = ?", user.Correo).Count(&count)
	if count > 0 {
		return errors.New("el correo ya está registrado")
	}

	fullName := strings.ToLower(strings.TrimSpace(user.Nombres_Apellidos))
	tx.Model(&models.Usuarios{}).Where("LOWER(TRIM(nombres_apellidos)) = ?", fullName).Count(&count)
	if count > 0 {
		s.logSecurityEvent("DUPLICATE_NAME_ATTEMPT", user.Usuario, "", 
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

	var user models.Usuarios
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
	go s.sendPasswordUpdateNotification(user.Correo, user.Nombres_Apellidos, clientIP)
	s.logSecurityEvent("PASSWORD_CHANGED", user.Usuario, "", "Contraseña actualizada")
	
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

func (s *userService) ListUsers() ([]models.Usuarios, error) {
	var users []models.Usuarios
	err := s.db.Preload("Roles").Find(&users).Error
	return users, err
}

func (s *userService) GetUserByID(id uint) (*models.Usuarios, error) {
	var user models.Usuarios
	err := s.db.Preload("Roles").First(&user, id).Error
	if err != nil {
		return nil, errors.New("usuario no encontrado")
	}
	return &user, nil
}

func (s *userService) UpdateUser(id uint, updates map[string]interface{}) error {
	var user models.Usuarios
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

	go s.sendProfileUpdateNotification(user.Correo, user.Nombres_Apellidos, updatedFields, "")
	s.logSecurityEvent("USER_UPDATED", fmt.Sprintf("user_id_%d", id), "", 
		fmt.Sprintf("Campos actualizados: %v", filteredUpdates))
	
	return nil
}

func (s *userService) DeleteUser(id uint) error {
	tx := s.db.Begin()
	defer tx.Rollback()

	var user models.Usuarios
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
	
	go s.sendAccountDeletionNotification(user.Correo, user.Nombres_Apellidos)
	s.logSecurityEvent("USER_DELETED", user.Usuario, "", "Usuario eliminado")
	
	return nil
}

func (s *userService) SetAccountStatus(id uint, status bool) error {
	var user models.Usuarios
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
	
	go emailFunc(user.Correo, user.Nombres_Apellidos)
	s.logSecurityEvent("ACCOUNT_STATUS_CHANGED", user.Usuario, "", 
		fmt.Sprintf("Cuenta %s", action))
	
	return nil
}

func (s *userService) UnlockAccount(userID uint, adminID uint) error {
	var user models.Usuarios
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

	s.logSecurityEvent("ACCOUNT_UNLOCKED", user.Usuario, "", 
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

func (s *userService) SearchUserByField(field, value string) ([]models.Usuarios, error) {
	if s.detectSQLInjection(value) {
		return nil, errors.New("búsqueda inválida")
	}

	var users []models.Usuarios
	searchPattern := "%" + strings.ToLower(value) + "%"
	
	err := s.db.Preload("Roles").
		Where("LOWER(nombres_apellidos) LIKE ? OR LOWER(correo) LIKE ? OR num_documento LIKE ?",
			searchPattern, searchPattern, searchPattern).
		Find(&users).Error
		
	return users, err
}

func (s *userService) CheckUserExists(field, value string) (bool, error) {
	var count int64
	err := s.db.Model(&models.Usuarios{}).Where(field+" = ?", value).Count(&count).Error
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
		s.db.Model(&models.Usuarios{}).Where("usuario = ?", username).Count(&count)
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
	subject := "Bienvenido a Serious Game - Activación de Cuenta"
	
	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;background-color:#f5f7fa;">
    <table width="100%%" cellpadding="0" cellspacing="0" style="min-width:320px;background-color:#f5f7fa;">
        <tr>
            <td align="center" style="padding:40px 20px;">
                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 6px rgba(0,0,0,0.07);">
                    <tr>
                        <td style="padding:0;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);padding:40px 30px;border-radius:16px 16px 0 0;text-align:center;">
                                        <h1 style="margin:0;color:#ffffff;font-size:28px;font-weight:700;letter-spacing:-0.5px;">Serious Game</h1>
                                        <p style="margin:8px 0 0;color:#e0e7ff;font-size:16px;">Plataforma educativa especializada</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:40px 30px;">
                                        <h2 style="margin:0 0 20px;color:#1a202c;font-size:24px;font-weight:600;">¡Bienvenido a nuestra comunidad!</h2>
                                        <p style="margin:0 0 25px;color:#4a5568;font-size:16px;line-height:1.6;">
                                            Estimado/a padre o tutor,<br><br>
                                            Nos complace darle la bienvenida a Serious Game, una plataforma diseñada especialmente para apoyar el desarrollo y aprendizaje de niños con necesidades especiales.
                                        </p>
                                        <div style="background-color:#edf2f7;border-radius:12px;padding:25px;margin:0 0 25px;">
                                            <h3 style="margin:0 0 15px;color:#2d3748;font-size:18px;font-weight:600;">Datos de acceso</h3>
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td style="padding:8px 0;">
                                                        <span style="color:#718096;font-size:14px;">Usuario:</span>
                                                        <span style="color:#2d3748;font-size:16px;font-weight:600;display:block;margin-top:4px;">%s</span>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;">
                                                        <span style="color:#718096;font-size:14px;">Contraseña temporal:</span>
                                                        <span style="color:#2d3748;font-size:16px;font-weight:600;font-family:monospace;display:block;margin-top:4px;background-color:#f7fafc;padding:8px 12px;border-radius:6px;border:1px solid #e2e8f0;">%s</span>
                                                    </td>
                                                </tr>
                                            </table>
                                        </div>
                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td align="center" style="padding:30px 0;">
                                                    <a href="%s" style="display:inline-block;padding:16px 40px;background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);color:#ffffff;text-decoration:none;font-size:16px;font-weight:600;border-radius:50px;box-shadow:0 4px 15px rgba(102,126,234,0.4);transition:all 0.3s ease;">
                                                        Activar mi cuenta
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                        <div style="background-color:#fff5f5;border-left:4px solid:#fc8181;padding:16px;border-radius:6px;margin:25px 0;">
                                            <p style="margin:0;color:#742a2a;font-size:14px;">
                                                <strong>Importante:</strong> Este enlace de activación expira en 24 horas por motivos de seguridad.
                                            </p>
                                        </div>
                                        <p style="margin:25px 0 0;color:#4a5568;font-size:14px;line-height:1.6;">
                                            Si tiene alguna pregunta o necesita asistencia, no dude en contactarnos. Estamos aquí para apoyarle en cada paso del camino.
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color:#f7fafc;padding:30px;border-radius:0 0 16px 16px;text-align:center;border-top:1px solid #e2e8f0;">
                                        <p style="margin:0 0 10px;color:#718096;font-size:13px;">
                                            © 2024 Serious Game - Universidad Nacional de Tumbes
                                        </p>
                                        <p style="margin:0;color:#a0aec0;font-size:12px;">
                                            Este es un correo automático, por favor no responda a esta dirección.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>`, username, tempPassword, activationURL)
	
	sendEmail(email, subject, body)
}
func (s *userService) sendAccountLockNotification(email, name, ip string) {
	subject := "🔒 Cuenta Bloqueada por Seguridad - Serious Game"
	
	location := getLocationFromIP(ip)
	unlockToken := generateUnlockToken(email)
	
	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;background-color:#fef2f2;">
    <table width="100%%" cellpadding="0" cellspacing="0" style="min-width:320px;background-color:#fef2f2;">
        <tr>
            <td align="center" style="padding:40px 20px;">
                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 12px rgba(0,0,0,0.15);border:2px solid #fecaca;">
                    <tr>
                        <td style="padding:0;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#dc2626 0%%,#991b1b 100%%);padding:40px 30px;border-radius:14px 14px 0 0;text-align:center;">
                                        <div style="display:inline-block;width:80px;height:80px;background-color:rgba(255,255,255,0.15);border-radius:50%%;padding:20px;margin-bottom:20px;">
                                            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                                <rect x="5" y="11" width="14" height="10" rx="2" stroke="white" stroke-width="2"/>
                                                <path d="M7 11V7C7 4.79086 8.79086 3 11 3H13C15.2091 3 17 4.79086 17 7V11" stroke="white" stroke-width="2" stroke-linecap="round"/>
                                                <circle cx="12" cy="16" r="1" fill="white"/>
                                            </svg>
                                        </div>
                                        <h1 style="margin:0;color:#ffffff;font-size:32px;font-weight:700;letter-spacing:-0.5px;">Cuenta Bloqueada</h1>
                                        <p style="margin:10px 0 0;color:#fee2e2;font-size:16px;">Medida de seguridad activada</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:40px 30px;">
                                        <p style="margin:0 0 24px;color:#1f2937;font-size:16px;line-height:1.6;">
                                            Estimado/a <strong style="color:#dc2626;">%s</strong>,
                                        </p>
                                        <p style="margin:0 0 28px;color:#4b5563;font-size:15px;line-height:1.7;">
                                            Por su seguridad, hemos bloqueado temporalmente el acceso a su cuenta después de detectar múltiples intentos de inicio de sesión fallidos.
                                        </p>
                                        <div style="background-color:#fee2e2;border-radius:12px;padding:24px;margin:0 0 28px;border-left:4px solid #dc2626;">
                                            <h3 style="margin:0 0 16px;color:#991b1b;font-size:16px;font-weight:600;display:flex;align-items:center;">
                                                <span style="margin-right:8px;">🚨</span> Detalles del incidente
                                            </h3>
                                            <table width="100%%" cellpadding="0" cellspacing="0" style="font-size:14px;">
                                                <tr>
                                                    <td style="padding:8px 0;color:#7f1d1d;width:140px;vertical-align:top;">Intentos fallidos:</td>
                                                    <td style="padding:8px 0;color:#991b1b;font-weight:600;">5 intentos consecutivos</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;color:#7f1d1d;vertical-align:top;">Dirección IP:</td>
                                                    <td style="padding:8px 0;color:#991b1b;font-family:monospace;font-weight:500;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;color:#7f1d1d;vertical-align:top;">Ubicación detectada:</td>
                                                    <td style="padding:8px 0;color:#991b1b;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;color:#7f1d1d;vertical-align:top;">Fecha y hora:</td>
                                                    <td style="padding:8px 0;color:#991b1b;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;color:#7f1d1d;vertical-align:top;">Código de referencia:</td>
                                                    <td style="padding:8px 0;color:#991b1b;font-family:monospace;font-weight:600;">#%s</td>
                                                </tr>
                                            </table>
                                        </div>
                                        <div style="background:linear-gradient(135deg,#e0f2fe 0%%,#bae6fd 100%%);border-radius:12px;padding:24px;margin:0 0 32px;border-left:4px solid #0284c7;">
                                            <h3 style="margin:0 0 16px;color:#075985;font-size:16px;font-weight:600;">
                                                🔓 Cómo desbloquear su cuenta
                                            </h3>
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td style="padding:8px 0;vertical-align:top;width:28px;">
                                                        <div style="width:24px;height:24px;background-color:#0284c7;color:#ffffff;border-radius:50%%;text-align:center;line-height:24px;font-size:12px;font-weight:600;">1</div>
                                                    </td>
                                                    <td style="padding:8px 0;color:#0c4a6e;font-size:14px;">
                                                        Verifique que reconoce estos intentos de acceso
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;vertical-align:top;">
                                                        <div style="width:24px;height:24px;background-color:#0284c7;color:#ffffff;border-radius:50%%;text-align:center;line-height:24px;font-size:12px;font-weight:600;">2</div>
                                                    </td>
                                                    <td style="padding:8px 0;color:#0c4a6e;font-size:14px;">
                                                        Si fueron intentos legítimos, espere 30 minutos antes de intentar nuevamente
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;vertical-align:top;">
                                                        <div style="width:24px;height:24px;background-color:#0284c7;color:#ffffff;border-radius:50%%;text-align:center;line-height:24px;font-size:12px;font-weight:600;">3</div>
                                                    </td>
                                                    <td style="padding:8px 0;color:#0c4a6e;font-size:14px;">
                                                        Si no reconoce la actividad, contacte a soporte inmediatamente
                                                    </td>
                                                </tr>
                                            </table>
                                        </div>
                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td align="center" style="padding:24px 0;">
                                                    <table cellpadding="0" cellspacing="0">
                                                        <tr>
                                                            <td style="padding:0 8px;">
                                                                <a href="https://seriousgame.com/unlock?token=%s" style="display:inline-block;padding:14px 32px;background-color:#dc2626;color:#ffffff;text-decoration:none;font-size:16px;font-weight:600;border-radius:8px;box-shadow:0 4px 6px rgba(0,0,0,0.1);">
                                                                    Desbloquear ahora
                                                                </a>
                                                            </td>
                                                            <td style="padding:0 8px;">
                                                                <a href="mailto:soporte@seriousgame.com?subject=Cuenta%%20Bloqueada%%20-%%20Código%%20%s" style="display:inline-block;padding:14px 32px;background-color:#ffffff;color:#dc2626;text-decoration:none;font-size:16px;font-weight:600;border-radius:8px;border:2px solid #dc2626;">
                                                                    Contactar soporte
                                                                </a>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>
                                        <div style="background-color:#fffbeb;border:1px solid #fcd34d;border-radius:12px;padding:20px;margin:32px 0 0;text-align:center;">
                                            <p style="margin:0 0 8px;color:#92400e;font-size:14px;font-weight:600;">
                                                ⏰ Tiempo de espera automático: 30 minutos
                                            </p>
                                            <p style="margin:0;color:#78350f;font-size:13px;">
                                                Su cuenta se desbloqueará automáticamente a las %s
                                            </p>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color:#f9fafb;padding:28px 30px;border-radius:0 0 14px 14px;text-align:center;border-top:1px solid #e5e7eb;">
                                        <p style="margin:0 0 8px;color:#6b7280;font-size:13px;">
                                            © %d Serious Game 
                                        </p>
                                        <p style="margin:0;color:#9ca3af;font-size:12px;">
                                            Sistema de Seguridad Automatizado
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>`, name, ip, location, time.Now().Format("02/01/2006 15:04:05 MST"), unlockToken[:8], unlockToken, unlockToken[:8], time.Now().Add(30*time.Minute).Format("15:04 MST"), time.Now().Year())
	
	sendEmail(email, subject, body)
}

func generateUnlockToken(email string) string {
	h := sha256.Sum256([]byte(email + time.Now().String()))
	return fmt.Sprintf("%X", h[:6])
}

func (s *userService) sendPasswordUpdateNotification(email, name, ip string) {
	subject := "Actualización de Contraseña - SERIOUS GAME"
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
				© %d Soporte Serious Game - Todos los derechos reservados
			</div>
		</div>
	</body>
	</html>`, name, time.Now().Format("02/01/2006 15:04:05"), ip, time.Now().Year())
	
	sendEmail(email, subject, body)
}

func (s *userService) sendProfileUpdateNotification(email, name string, fields []string, ip string) {
	subject := "Perfil Actualizado - Serious Game"
	
	fieldTranslations := map[string]struct {
		name string
		icon string
	}{
		"telefono":         {"Número de celular", "📱"},
		"correo":          {"Correo electrónico", "📧"},
		"foto":            {"Foto de perfil", "📷"},
		"nombres_apellidos":         {"Nombres", "👤"},
		"tipo_documento":  {"Tipo de documento", "🆔"},
		"num_documento":   {"Número de documento", "🔢"},
		"fecha_nacimiento": {"Fecha de nacimiento", "📅"},
		"preferencias":    {"Preferencias", "⚙️"},
		"password":        {"Contraseña", "🔐"},
	}
	
	fieldsHTML := ""
	for _, field := range fields {
		fieldInfo := fieldTranslations[field]
		if fieldInfo.name == "" {
			fieldInfo.name = field
			fieldInfo.icon = "📝"
		}
		fieldsHTML += fmt.Sprintf(`
			<tr>
				<td style="padding:8px 0;width:30px;text-align:center;font-size:18px;">%s</td>
				<td style="padding:8px 0;color:#1f2937;font-size:14px;">%s</td>
			</tr>`, fieldInfo.icon, fieldInfo.name)
	}
	
	location := getLocationFromIP(ip)
	
	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;background-color:#f0f9ff;">
    <table width="100%%" cellpadding="0" cellspacing="0" style="min-width:320px;background-color:#f0f9ff;">
        <tr>
            <td align="center" style="padding:40px 20px;">
                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 6px rgba(0,0,0,0.07);">
                    <tr>
                        <td style="padding:0;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#3b82f6 0%%,#2563eb 100%%);padding:40px 30px;border-radius:16px 16px 0 0;text-align:center;">
                                        <div style="display:inline-block;width:64px;height:64px;background-color:rgba(255,255,255,0.2);border-radius:50%%;padding:16px;margin-bottom:20px;">
                                            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                                <path d="M12 15C13.6569 15 15 13.6569 15 12C15 10.3431 13.6569 9 12 9C10.3431 9 9 10.3431 9 12C9 13.6569 10.3431 15 12 15Z" stroke="white" stroke-width="2"/>
                                                <path d="M19.4 15C19.2 15.4 18.9 15.7 18.6 16L17 17.6C16.7 17.9 16.4 18.2 16 18.4C15.6 18.6 15.2 18.7 14.8 18.7C14.4 18.7 14 18.6 13.6 18.4L12 17.4L10.4 18.4C10 18.6 9.6 18.7 9.2 18.7C8.8 18.7 8.4 18.6 8 18.4C7.6 18.2 7.3 17.9 7 17.6L5.4 16C5.1 15.7 4.8 15.4 4.6 15C4.4 14.6 4.3 14.2 4.3 13.8C4.3 13.4 4.4 13 4.6 12.6L5.6 11L4.6 9.4C4.4 9 4.3 8.6 4.3 8.2C4.3 7.8 4.4 7.4 4.6 7C4.8 6.6 5.1 6.3 5.4 6L7 4.4C7.3 4.1 7.6 3.8 8 3.6C8.4 3.4 8.8 3.3 9.2 3.3C9.6 3.3 10 3.4 10.4 3.6L12 4.6L13.6 3.6C14 3.4 14.4 3.3 14.8 3.3C15.2 3.3 15.6 3.4 16 3.6C16.4 3.8 16.7 4.1 17 4.4L18.6 6C18.9 6.3 19.2 6.6 19.4 7C19.6 7.4 19.7 7.8 19.7 8.2C19.7 8.6 19.6 9 19.4 9.4L18.4 11L19.4 12.6C19.6 13 19.7 13.4 19.7 13.8C19.7 14.2 19.6 14.6 19.4 15Z" stroke="white" stroke-width="2"/>
                                            </svg>
                                        </div>
                                        <h1 style="margin:0;color:#ffffff;font-size:28px;font-weight:700;letter-spacing:-0.5px;">Perfil Actualizado</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:40px 30px;">
                                        <p style="margin:0 0 24px;color:#1f2937;font-size:16px;line-height:1.6;">
                                            Hola <strong style="color:#2563eb;">%s</strong>,
                                        </p>
                                        <p style="margin:0 0 28px;color:#4b5563;font-size:15px;line-height:1.7;">
                                            Te informamos que se han realizado cambios en tu perfil de Serious Game.
                                        </p>
                                        <div style="background-color:#eff6ff;border-radius:12px;padding:24px;margin:0 0 28px;border:1px solid #dbeafe;">
                                            <h3 style="margin:0 0 16px;color:#1e40af;font-size:16px;font-weight:600;">
                                                📋 Campos actualizados
                                            </h3>
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                %s
                                            </table>
                                        </div>
                                        <div style="background-color:#f8fafc;border-radius:12px;padding:20px;margin:0 0 28px;">
                                            <h4 style="margin:0 0 12px;color:#475569;font-size:14px;font-weight:600;">
                                                Detalles de la actualización
                                            </h4>
                                            <table width="100%%" cellpadding="0" cellspacing="0" style="font-size:13px;">
                                                <tr>
                                                    <td style="padding:6px 0;color:#6b7280;width:100px;">Fecha:</td>
                                                    <td style="padding:6px 0;color:#374151;font-weight:500;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;color:#6b7280;">Dirección IP:</td>
                                                    <td style="padding:6px 0;color:#374151;font-family:monospace;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;color:#6b7280;">Ubicación:</td>
                                                    <td style="padding:6px 0;color:#374151;">%s</td>
                                                </tr>
                                            </table>
                                        </div>
                                        <div style="background-color:#fef2f2;border-left:4px solid #ef4444;padding:20px;border-radius:6px;margin:0 0 32px;">
                                            <h4 style="margin:0 0 8px;color:#991b1b;font-size:15px;font-weight:600;display:flex;align-items:center;">
                                                <span style="margin-right:8px;">⚠️</span> ¿No reconoces estos cambios?
                                            </h4>
                                            <p style="margin:0 0 12px;color:#7f1d1d;font-size:14px;line-height:1.5;">
                                                Si no realizaste estas modificaciones, tu cuenta podría estar comprometida.
                                            </p>
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td>
                                                        <a href="https://seriousgame.com/security/secure-account" style="display:inline-block;padding:10px 20px;background-color:#dc2626;color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;border-radius:6px;">
                                                            Asegurar mi cuenta
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>
                                        </div>
                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td style="text-align:center;padding:20px 0;">
                                                    <p style="margin:0 0 12px;color:#6b7280;font-size:14px;">
                                                        ¿Tienes preguntas sobre estos cambios?
                                                    </p>
                                                    <a href="https://seriousgame.com/profile" style="display:inline-block;padding:12px 28px;background-color:#f3f4f6;color:#4b5563;text-decoration:none;font-size:14px;font-weight:600;border-radius:8px;border:1px solid #e5e7eb;">
                                                        Ver mi perfil completo
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color:#f9fafb;padding:28px 30px;border-radius:0 0 16px 16px;text-align:center;border-top:1px solid #e5e7eb;">
                                        <p style="margin:0 0 8px;color:#6b7280;font-size:13px;">
                                            © %d Serious Game 
                                        </p>
                                        <p style="margin:0;color:#9ca3af;font-size:12px;">
                                            Notificación automática de seguridad
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>`, name, fieldsHTML, time.Now().Format("02/01/2006 15:04:05 MST"), ip, location, time.Now().Year())
	
	sendEmail(email, subject, body)
}


func (s *userService) sendAccountActivatedEmail(email, name string) {
	subject := "✅ Cuenta Activada Exitosamente - Serious Game"
	
	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;background-color:#f0fdf4;">
    <table width="100%%" cellpadding="0" cellspacing="0" style="min-width:320px;background-color:#f0fdf4;">
        <tr>
            <td align="center" style="padding:40px 20px;">
                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 6px rgba(0,0,0,0.07);">
                    <tr>
                        <td style="padding:0;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#10b981 0%%,#059669 100%%);padding:40px 30px;border-radius:16px 16px 0 0;text-align:center;">
                                        <div style="display:inline-block;width:80px;height:80px;background-color:rgba(255,255,255,0.2);border-radius:50%%;padding:20px;margin-bottom:20px;">
                                            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                                <path d="M9 11L12 14L22 4" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                                                <path d="M21 12V19C21 20.1046 20.1046 21 19 21H5C3.89543 21 3 20.1046 3 19V5C3 3.89543 3.89543 3 5 3H16" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                            </svg>
                                        </div>
                                        <h1 style="margin:0;color:#ffffff;font-size:32px;font-weight:700;letter-spacing:-0.5px;">¡Cuenta Activada!</h1>
                                        <p style="margin:10px 0 0;color:#d1fae5;font-size:18px;">Todo está listo para comenzar</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:40px 30px;">
                                        <p style="margin:0 0 24px;color:#1f2937;font-size:18px;line-height:1.6;">
                                            ¡Bienvenido/a <strong style="color:#059669;">%s</strong>!
                                        </p>
                                        <p style="margin:0 0 28px;color:#4b5563;font-size:15px;line-height:1.7;">
                                            Su cuenta en Serious Game ha sido activada exitosamente. Ya puede acceder a todas las funcionalidades de nuestra plataforma educativa especializada.
                                        </p>
                                        <div style="background:linear-gradient(135deg,#ecfdf5 0%%,#d1fae5 100%%);border-radius:12px;padding:24px;margin:0 0 32px;border-left:4px solid #10b981;">
                                            <h3 style="margin:0 0 16px;color:#047857;font-size:16px;font-weight:600;">
                                                ✨ Próximos pasos recomendados
                                            </h3>
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td style="padding:6px 0;vertical-align:top;width:24px;">
                                                        <span style="color:#10b981;font-size:16px;">1.</span>
                                                    </td>
                                                    <td style="padding:6px 0;color:#065f46;font-size:14px;">
                                                        Complete el perfil de su hijo/a para personalizar la experiencia
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;vertical-align:top;">
                                                        <span style="color:#10b981;font-size:16px;">2.</span>
                                                    </td>
                                                    <td style="padding:6px 0;color:#065f46;font-size:14px;">
                                                        Explore las actividades educativas disponibles
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;vertical-align:top;">
                                                        <span style="color:#10b981;font-size:16px;">3.</span>
                                                    </td>
                                                    <td style="padding:6px 0;color:#065f46;font-size:14px;">
                                                        Configure las preferencias de aprendizaje
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;vertical-align:top;">
                                                        <span style="color:#10b981;font-size:16px;">4.</span>
                                                    </td>
                                                    <td style="padding:6px 0;color:#065f46;font-size:14px;">
                                                        Revise la guía para padres en el centro de ayuda
                                                    </td>
                                                </tr>
                                            </table>
                                        </div>
                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td align="center" style="padding:32px 0;">
                                                    <a href="https://seriousgame.com/login" style="display:inline-block;padding:16px 48px;background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);color:#ffffff;text-decoration:none;font-size:18px;font-weight:600;border-radius:50px;box-shadow:0 4px 15px rgba(102,126,234,0.4);transition:all 0.3s ease;">
                                                        Acceder al Portal
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                        <div style="background-color:#f3f4f6;border-radius:12px;padding:20px;margin:24px 0;text-align:center;">
                                            <p style="margin:0 0 8px;color:#4b5563;font-size:14px;">
                                                <strong>Horario de atención:</strong>
                                            </p>
                                            <p style="margin:0;color:#6b7280;font-size:13px;">
                                                Lunes a Viernes: 8:00 AM - 6:00 PM<br>
                                                Sábados: 9:00 AM - 1:00 PM
                                            </p>
                                        </div>
                                        <table width="100%%" cellpadding="0" cellspacing="0" style="margin-top:32px;">
                                            <tr>
                                                <td style="text-align:center;">
                                                    <p style="margin:0 0 16px;color:#6b7280;font-size:14px;">
                                                        ¿Necesita ayuda? Estamos aquí para apoyarle
                                                    </p>
                                                    <table align="center" cellpadding="0" cellspacing="0">
                                                        <tr>
                                                            <td style="padding:0 8px;">
                                                                <a href="mailto:soporte@untumbes.edu.pe" style="display:inline-block;padding:10px 20px;background-color:#f3f4f6;color:#4b5563;text-decoration:none;font-size:14px;font-weight:500;border-radius:6px;border:1px solid #e5e7eb;">
                                                                    📧 Email
                                                                </a>
                                                            </td>
                                                            <td style="padding:0 8px;">
                                                                <a href="https://seriousgame.com/help" style="display:inline-block;padding:10px 20px;background-color:#f3f4f6;color:#4b5563;text-decoration:none;font-size:14px;font-weight:500;border-radius:6px;border:1px solid #e5e7eb;">
                                                                    📚 Centro de Ayuda
                                                                </a>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color:#f9fafb;padding:28px 30px;border-radius:0 0 16px 16px;text-align:center;border-top:1px solid #e5e7eb;">
                                        <p style="margin:0 0 8px;color:#6b7280;font-size:13px;">
                                            © %d Serious Game - 
                                        </p>
                                        <p style="margin:0;color:#9ca3af;font-size:12px;">
                                            Plataforma educativa para niños con autismo
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>`, name, time.Now().Year())
	
	sendEmail(email, subject, body)
}


func (s *userService) sendAccountDeactivatedEmail(email, name string) {
	subject := "Notificación: Cuenta Temporalmente Desactivada - Serious Game"
	
	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;background-color:#fef3c7;">
    <table width="100%%" cellpadding="0" cellspacing="0" style="min-width:320px;background-color:#fef3c7;">
        <tr>
            <td align="center" style="padding:40px 20px;">
                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 6px rgba(0,0,0,0.07);border:1px solid #fbbf24;">
                    <tr>
                        <td style="padding:0;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#f59e0b 0%%,#d97706 100%%);padding:40px 30px;border-radius:15px 15px 0 0;text-align:center;">
                                        <div style="display:inline-block;width:64px;height:64px;background-color:rgba(255,255,255,0.2);border-radius:50%%;padding:16px;margin-bottom:20px;">
                                            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                                <path d="M12 15V12M12 9H12.01M13 5H11C10.4477 5 10 5.44772 10 6V7C10 7.55228 10.4477 8 11 8H13C13.5523 8 14 7.55228 14 7V6C14 5.44772 13.5523 5 13 5Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                                <path d="M10 8H14C15.1046 8 16 8.89543 16 10V18C16 19.1046 15.1046 20 14 20H10C8.89543 20 8 19.1046 8 18V10C8 8.89543 8.89543 8 10 8Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                            </svg>
                                        </div>
                                        <h1 style="margin:0;color:#ffffff;font-size:28px;font-weight:700;letter-spacing:-0.5px;">Cuenta Temporalmente Desactivada</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:40px 30px;">
                                        <p style="margin:0 0 24px;color:#1f2937;font-size:16px;line-height:1.6;">
                                            Estimado/a <strong style="color:#111827;">%s</strong>,
                                        </p>
                                        <p style="margin:0 0 28px;color:#4b5563;font-size:15px;line-height:1.7;">
                                            Le informamos que su cuenta en Serious Game ha sido temporalmente desactivada como medida preventiva.
                                        </p>
                                        <div style="background-color:#fffbeb;border:1px solid #fcd34d;border-radius:12px;padding:24px;margin:0 0 28px;">
                                            <h3 style="margin:0 0 12px;color:#92400e;font-size:16px;font-weight:600;display:flex;align-items:center;">
                                                <span style="margin-right:8px;">⚠️</span> Posibles razones
                                            </h3>
                                            <ul style="margin:0;padding-left:24px;color:#78350f;font-size:14px;line-height:1.8;">
                                                <li>Actividad inusual detectada en la cuenta</li>
                                                <li>Actualización de políticas de seguridad</li>
                                                <li>Verificación administrativa pendiente</li>
                                                <li>Mantenimiento del sistema</li>
                                            </ul>
                                        </div>
                                        <div style="background:linear-gradient(135deg,#e0f2fe 0%%,#bae6fd 100%%);border-radius:12px;padding:24px;margin:0 0 32px;border-left:4px solid #0284c7;">
                                            <h3 style="margin:0 0 12px;color:#075985;font-size:16px;font-weight:600;">
                                                ¿Qué puede hacer?
                                            </h3>
                                            <ol style="margin:0;padding-left:24px;color:#0c4a6e;font-size:14px;line-height:2;">
                                                <li>Contacte con nuestro equipo de soporte</li>
                                                <li>Proporcione su número de cuenta: <code style="background-color:#f0f9ff;padding:2px 6px;border-radius:4px;font-family:monospace;">#%s</code></li>
                                                <li>Espere la respuesta en 24-48 horas hábiles</li>
                                            </ol>
                                        </div>
                                        <table width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 32px;">
                                            <tr>
                                                <td style="background-color:#f3f4f6;border-radius:12px;padding:20px;text-align:center;">
                                                    <p style="margin:0 0 4px;color:#6b7280;font-size:14px;">
                                                        <strong>Importante:</strong> Durante este período no podrá acceder a la plataforma
                                                    </p>
                                                    <p style="margin:0;color:#9ca3af;font-size:13px;">
                                                        Lamentamos los inconvenientes que esto pueda causar
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td align="center" style="padding:20px 0;">
                                                    <a href="mailto:soporte@untumbes.edu.pe?subject=Solicitud%%20de%%20Reactivación%%20-%%20Cuenta%%20%s" style="display:inline-block;padding:14px 32px;background:linear-gradient(135deg,#3b82f6 0%%,#2563eb 100%%);color:#ffffff;text-decoration:none;font-size:16px;font-weight:600;border-radius:8px;box-shadow:0 4px 6px rgba(0,0,0,0.1);">
                                                        Contactar Soporte
                                                    </a>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td align="center" style="padding:0 0 20px;">
                                                    <p style="margin:0;color:#6b7280;font-size:13px;">
                                                        o escriba directamente a: <a href="mailto:soporte@seriousgame.com style="color:#3b82f6;text-decoration:none;">soporte@untumbes.edu.pe</a>
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color:#f9fafb;padding:28px 30px;border-radius:0 0 15px 15px;text-align:center;border-top:1px solid #e5e7eb;">
                                        <p style="margin:0 0 8px;color:#6b7280;font-size:13px;">
                                            © %d Serious Game - 
                                        </p>
                                        <p style="margin:0;color:#9ca3af;font-size:12px;">
                                            Departamento de Soporte Técnico
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>`, name, generateAccountID(email), generateAccountID(email), time.Now().Year())
	
	sendEmail(email, subject, body)
}

func generateAccountID(email string) string {
	h := sha256.Sum256([]byte(email + time.Now().Format("2006")))
	return fmt.Sprintf("%X", h[:4])
}

func (s *userService) sendAccountDeletionNotification(email, name string) {
	subject := "Confirmación de Eliminación de Cuenta - Serious Game"
	
	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;background-color:#f8fafc;">
    <table width="100%%" cellpadding="0" cellspacing="0" style="min-width:320px;background-color:#f8fafc;">
        <tr>
            <td align="center" style="padding:40px 20px;">
                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;box-shadow:0 2px 8px rgba(0,0,0,0.05);">
                    <tr>
                        <td style="padding:0;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#64748b 0%%,#475569 100%%);padding:40px 30px;border-radius:16px 16px 0 0;text-align:center;">
                                        <div style="display:inline-block;width:64px;height:64px;background-color:rgba(255,255,255,0.15);border-radius:50%%;padding:16px;margin-bottom:20px;">
                                            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                                <path d="M9 21H15M12 3V7M4.59 5.59L7.41 8.41M19.41 5.59L16.59 8.41M6 12H2M22 12H18M4.59 18.41L7.41 15.59M19.41 18.41L16.59 15.59M12 12L8 16M12 12L16 16" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" opacity="0.8"/>
                                            </svg>
                                        </div>
                                        <h1 style="margin:0;color:#ffffff;font-size:28px;font-weight:700;letter-spacing:-0.5px;">Cuenta Eliminada</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:40px 30px;">
                                        <p style="margin:0 0 24px;color:#1e293b;font-size:16px;line-height:1.6;">
                                            Estimado/a <strong style="color:#0f172a;">%s</strong>,
                                        </p>
                                        <p style="margin:0 0 28px;color:#475569;font-size:15px;line-height:1.7;">
                                            Confirmamos que su cuenta en Serious Game ha sido eliminada exitosamente según lo solicitado.
                                        </p>
                                        <div style="background:linear-gradient(135deg,#f1f5f9 0%%,#e2e8f0 100%%);border-radius:12px;padding:24px;margin:0 0 28px;border-left:4px solid #94a3b8;">
                                            <h3 style="margin:0 0 12px;color:#334155;font-size:16px;font-weight:600;">
                                                ✓ Acciones completadas
                                            </h3>
                                            <ul style="margin:0;padding-left:20px;color:#64748b;font-size:14px;line-height:1.8;">
                                                <li>Datos personales eliminados permanentemente</li>
                                                <li>Progreso y actividades removidos</li>
                                                <li>Configuraciones de cuenta borradas</li>
                                                <li>Acceso a la plataforma revocado</li>
                                            </ul>
                                        </div>
                                        <div style="background-color:#dbeafe;border-radius:12px;padding:20px;margin:0 0 32px;border:1px solid #93c5fd;">
                                            <p style="margin:0;color:#1e40af;font-size:14px;line-height:1.6;">
                                                <strong>Nota importante:</strong> Si en el futuro desea volver a utilizar nuestros servicios, podrá crear una nueva cuenta realizando el proceso de registro completo.
                                            </p>
                                        </div>
                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td style="padding:24px;background-color:#fafafa;border-radius:12px;text-align:center;">
                                                    <p style="margin:0 0 12px;color:#64748b;font-size:15px;">
                                                        Agradecemos el tiempo que compartió con nosotros.
                                                    </p>
                                                    <p style="margin:0;color:#94a3b8;font-size:14px;">
                                                        Si tiene alguna pregunta, estamos aquí para ayudarle.
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                        
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color:#f8fafc;padding:28px 30px;border-radius:0 0 16px 16px;text-align:center;border-top:1px solid #e2e8f0;">
                                        <p style="margin:0 0 8px;color:#94a3b8;font-size:13px;">
                                            © %d Serious Game
                                        </p>
                                        <p style="margin:0;color:#cbd5e1;font-size:12px;">
                                            Este correo confirma la eliminación solicitada de su cuenta
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>`, name, time.Now().Year())
	
	sendEmail(email, subject, body)
}

func (s *userService) sendSecurityAlert(user *models.Usuarios, ip, reason string) {
	subject := "⚠️ Alerta de Seguridad - Serious Game"
	
	reasonMap := map[string]string{
		"multiple_failed_attempts": "Múltiples intentos de acceso fallidos",
		"unusual_location": "Acceso desde ubicación inusual",
		"password_reset": "Solicitud de cambio de contraseña",
		"suspicious_activity": "Actividad sospechosa detectada",
		"new_device": "Acceso desde dispositivo no reconocido",
	}
	
	reasonText := reasonMap[reason]
	if reasonText == "" {
		reasonText = reason
	}
	
	location := getLocationFromIP(ip)
	
	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;background-color:#fef2f2;">
    <table width="100%%" cellpadding="0" cellspacing="0" style="min-width:320px;background-color:#fef2f2;">
        <tr>
            <td align="center" style="padding:40px 20px;">
                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 6px rgba(0,0,0,0.07);border:2px solid #fecaca;">
                    <tr>
                        <td style="padding:0;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#ef4444 0%%,#dc2626 100%%);padding:30px;border-radius:14px 14px 0 0;text-align:center;">
                                        <div style="display:inline-block;width:60px;height:60px;background-color:rgba(255,255,255,0.2);border-radius:50%%;padding:15px;margin-bottom:15px;">
                                            <svg width="60" height="60" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                                <path d="M12 9V13M12 17H12.01M5.07183 19H18.9282C20.4678 19 21.4301 17.3333 20.6603 16L13.7321 4C12.9623 2.66667 11.0377 2.66667 10.2679 4L3.33975 16C2.56995 17.3333 3.53223 19 5.07183 19Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                            </svg>
                                        </div>
                                        <h1 style="margin:0;color:#ffffff;font-size:26px;font-weight:700;">Alerta de Seguridad</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:40px 30px;">
                                        <p style="margin:0 0 20px;color:#374151;font-size:16px;line-height:1.6;">
                                            Hola <strong>%s</strong>,
                                        </p>
                                        <p style="margin:0 0 25px;color:#4b5563;font-size:15px;line-height:1.6;">
                                            Hemos detectado actividad inusual en su cuenta que requiere su atención inmediata.
                                        </p>
                                        <div style="background-color:#fef3c7;border:1px solid #fcd34d;border-radius:12px;padding:20px;margin:0 0 25px;">
                                            <h3 style="margin:0 0 15px;color:#92400e;font-size:16px;font-weight:600;display:flex;align-items:center;">
                                                <span style="margin-right:8px;">⚠️</span> Detalles de la actividad
                                            </h3>
                                            <table width="100%%" cellpadding="0" cellspacing="0" style="font-size:14px;">
                                                <tr>
                                                    <td style="padding:8px 0;color:#6b7280;width:120px;vertical-align:top;">Tipo de alerta:</td>
                                                    <td style="padding:8px 0;color:#111827;font-weight:600;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;color:#6b7280;vertical-align:top;">Dirección IP:</td>
                                                    <td style="padding:8px 0;color:#111827;font-family:monospace;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;color:#6b7280;vertical-align:top;">Ubicación:</td>
                                                    <td style="padding:8px 0;color:#111827;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0;color:#6b7280;vertical-align:top;">Fecha y hora:</td>
                                                    <td style="padding:8px 0;color:#111827;">%s</td>
                                                </tr>
                                            </table>
                                        </div>
                                        <div style="background-color:#fee2e2;border-radius:12px;padding:20px;margin:0 0 30px;">
                                            <h4 style="margin:0 0 10px;color:#991b1b;font-size:16px;font-weight:600;">¿No reconoce esta actividad?</h4>
                                            <p style="margin:0 0 15px;color:#7f1d1d;font-size:14px;line-height:1.5;">
                                                Si no ha sido usted quien realizó esta acción, su cuenta podría estar comprometida. Le recomendamos:
                                            </p>
                                            <ol style="margin:0;padding-left:20px;color:#7f1d1d;font-size:14px;line-height:1.8;">
                                                <li>Cambiar su contraseña inmediatamente</li>
                                                <li>Revisar la actividad reciente de su cuenta</li>
                                                <li>Activar la autenticación de dos factores</li>
                                                <li>Contactar con soporte si necesita ayuda</li>
                                            </ol>
                                        </div>
                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td align="center" style="padding:20px 0;">
                                                    <a href="https://portal.untumbes.edu.pe/security/change-password" style="display:inline-block;padding:14px 32px;background-color:#dc2626;color:#ffffff;text-decoration:none;font-size:16px;font-weight:600;border-radius:8px;box-shadow:0 4px 6px rgba(0,0,0,0.1);">
                                                        Cambiar contraseña ahora
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                        <div style="margin-top:30px;padding-top:30px;border-top:1px solid #e5e7eb;">
                                            <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6;text-align:center;">
                                                Si reconoce esta actividad, puede ignorar este mensaje de forma segura.<br>
                                                Para más información sobre la seguridad de su cuenta, visite nuestro centro de ayuda.
                                            </p>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color:#f9fafb;padding:25px 30px;border-radius:0 0 14px 14px;text-align:center;border-top:1px solid #e5e7eb;">
                                        <p style="margin:0 0 8px;color:#6b7280;font-size:12px;">
                                            Este es un mensaje automático del sistema de seguridad
                                        </p>
                                        <p style="margin:0;color:#9ca3af;font-size:11px;">
                                            © 2025 Serious Game
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>`, user.Nombres_Apellidos, reasonText, ip, location, time.Now().Format("02/01/2006 15:04:05 MST"))
	
	sendEmail(user.Correo, subject, body)
}

func sendEmail(toEmail, subject, bodyHTML string) error {
	cfg := &emailConfig{
		host:     getEnvOrDefault("SMTP_HOST", "smtp.gmail.com"),
		port:     getEnvOrDefault("SMTP_PORT", "587"),
		email:    os.Getenv("SMTP_EMAIL"),
		password: os.Getenv("SMTP_PASSWORD"),
	}
	
	if cfg.email == "" || cfg.password == "" {
		return fmt.Errorf("credenciales SMTP no configuradas")
	}
	
	auth := smtp.PlainAuth("", cfg.email, cfg.password, cfg.host)
	
	headers := map[string]string{
		"From":         fmt.Sprintf("Serious Game <%s>", cfg.email),
		"To":           toEmail,
		"Subject":      subject,
		"MIME-Version": "1.0",
		"Content-Type": "text/html; charset=UTF-8",
		"X-Priority":   "3",
		"X-Mailer":     "Serious Game Mailer",
		"Date":         time.Now().Format(time.RFC1123Z),
	}
	
	var msg bytes.Buffer
	for k, v := range headers {
		msg.WriteString(fmt.Sprintf("%s: %s\r\n", k, v))
	}
	msg.WriteString("\r\n")
	msg.WriteString(bodyHTML)
	
	addr := fmt.Sprintf("%s:%s", cfg.host, cfg.port)
	
	if err := smtp.SendMail(addr, auth, cfg.email, []string{toEmail}, msg.Bytes()); err != nil {
		log.Printf("[EMAIL_ERROR] Fallo al enviar a %s: %v", toEmail, err)
		return fmt.Errorf("error enviando email: %w", err)
	}
	
	log.Printf("[EMAIL_SENT] Email enviado exitosamente a %s", toEmail)
	return nil
}

type emailConfig struct {
	host     string
	port     string
	email    string
	password string
}

func getEnvOrDefault(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getLocationFromIP(ip string) string {
	locations := map[string]string{
		"127.0.0.1": "Servidor local",
		"::1":       "Servidor local",
	}
	
	if loc, exists := locations[ip]; exists {
		return loc
	}
	
	if strings.HasPrefix(ip, "192.168.") || strings.HasPrefix(ip, "10.") {
		return "Red local"
	}
	
	return "Ubicación desconocida"
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