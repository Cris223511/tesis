package models

import (
    "time"
)

type BiometricCredential struct {
    ID              uint      `gorm:"primaryKey"`
    UserID          uint      `gorm:"index;not null"`
    CredentialID    []byte    `gorm:"type:varbinary(255);uniqueIndex;not null"`
    PublicKey       []byte    `gorm:"type:longblob;not null"`
    SignCount       uint32    `gorm:"not null"`
    Transports      string
    DeviceName      string    `gorm:"type:varchar(100)"` 
    DeviceType      string    `gorm:"type:varchar(50)"` 
    LastUsed        time.Time
    CreatedAt       time.Time
}