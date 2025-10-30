package utils

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
)


type SecureOTP struct {
	key []byte
}

var secureOTPInstance *SecureOTP


func GetSecureOTP() *SecureOTP {
	if secureOTPInstance == nil {
		masterKey := os.Getenv("OTP_MASTER_KEY")
		if masterKey == "" {
			
			masterKey = "secure_otp_key_dev_change_in_prod_2024"
		}
		secureOTPInstance = &SecureOTP{
			key: deriveKey(masterKey),
		}
	}
	return secureOTPInstance
}


func deriveKey(masterKey string) []byte {
	hash := sha256.Sum256([]byte(masterKey + "otp_salt_2024"))
	return hash[:]
}


func (s *SecureOTP) EncryptOTP(code string) (string, string, string, error) {
	if len(code) != 6 {
		return "", "", "", errors.New("código debe ser de 6 caracteres")
	}

	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return "", "", "", fmt.Errorf("error generando salt: %w", err)
	}
	saltHex := hex.EncodeToString(salt)


	hash := s.createHash(code, saltHex)


	encryptedCode, err := s.encrypt(code, saltHex)
	if err != nil {
		return "", "", "", fmt.Errorf("error encriptando: %w", err)
	}

	return encryptedCode, hash, saltHex, nil
}


func (s *SecureOTP) VerifyOTP(inputCode, storedHash, salt string) bool {
	if len(inputCode) != 6 || storedHash == "" || salt == "" {
		return false
	}


	inputHash := s.createHash(inputCode, salt)

	
	return subtle.ConstantTimeCompare([]byte(inputHash), []byte(storedHash)) == 1
}


func (s *SecureOTP) DecryptOTP(encryptedCode, salt string) (string, error) {
	return s.decrypt(encryptedCode, salt)
}


func (s *SecureOTP) createHash(code, salt string) string {
	data := code + salt + string(s.key[:8])
	hash := sha256.Sum256([]byte(data))
	return hex.EncodeToString(hash[:])
}


func (s *SecureOTP) encrypt(plaintext, salt string) (string, error) {

	keyData := append(s.key, []byte(salt)...)
	derivedKey := sha256.Sum256(keyData)

	block, err := aes.NewCipher(derivedKey[:])
	if err != nil {
		return "", err
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}

	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}

	ciphertext := gcm.Seal(nonce, nonce, []byte(plaintext), nil)
	return base64.StdEncoding.EncodeToString(ciphertext), nil
}


func (s *SecureOTP) decrypt(ciphertext, salt string) (string, error) {
	data, err := base64.StdEncoding.DecodeString(ciphertext)
	if err != nil {
		return "", err
	}

	keyData := append(s.key, []byte(salt)...)
	derivedKey := sha256.Sum256(keyData)

	block, err := aes.NewCipher(derivedKey[:])
	if err != nil {
		return "", err
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}

	nonceSize := gcm.NonceSize()
	if len(data) < nonceSize {
		return "", errors.New("ciphertext muy corto")
	}

	nonce, cipherbytes := data[:nonceSize], data[nonceSize:]
	plaintext, err := gcm.Open(nil, nonce, cipherbytes, nil)
	if err != nil {
		return "", err
	}

	return string(plaintext), nil
}


func IsOTPEncrypted(code, encryptedCode, hash, salt string) bool {
	
	return encryptedCode != "" && hash != "" && salt != ""
}


func (s *SecureOTP) MigrateOTPToEncrypted(legacyCode string) (string, string, string, error) {
	if legacyCode == "" {
		return "", "", "", errors.New("código legacy vacío")
	}
	return s.EncryptOTP(strings.ToUpper(legacyCode))
}