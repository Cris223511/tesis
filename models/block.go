package models

import "time"
type UserUnblockCooldown struct {

    BlockerID   uint      `gorm:"not null"`

    UnblockedID uint      `gorm:"not null"`

    UnblockedAt time.Time `gorm:"not null"`

    ExpireAt    time.Time `gorm:"not null"`

}