package models

import (
	"time"
	"golang.org/x/crypto/bcrypt"
)

type PasswordHistory struct {
	ID           uint      `gorm:"primaryKey"`
	UserID       uint      `gorm:"column:user_id;not null;index"`
	PasswordHash string    `gorm:"type:varchar(255);not null"`
	ChangedBy    uint      `gorm:"index"` 
	ChangeReason string    `gorm:"type:varchar(100)"` 
	IPAddress    string    `gorm:"type:varchar(45)"`
	CreatedAt    time.Time `gorm:"index"`
	Usuarios     Usuarios  `gorm:"foreignKey:UserID"`
}

func (ph *PasswordHistory) SetPassword(password string) error {
	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	ph.PasswordHash = string(hashedPassword)
	return nil
}

func (ph *PasswordHistory) CheckPassword(password string) bool {
	return bcrypt.CompareHashAndPassword([]byte(ph.PasswordHash), []byte(password)) == nil
}