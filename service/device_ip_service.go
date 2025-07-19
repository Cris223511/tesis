package services

import (
	"time"

	"gorm.io/gorm"
	"usuarios/models"
)

type DeviceIPRepo struct{ db *gorm.DB }

func NewDeviceIPRepo(db *gorm.DB) *DeviceIPRepo { return &DeviceIPRepo{db} }

func (r *DeviceIPRepo) GetIPSet(uid uint, dev string) (map[string]struct{}, error) {
	var rows []models.UserDeviceIP
	if err := r.db.Where("user_id = ? AND device = ?", uid, dev).
		Order("created_at").
		Find(&rows).Error; err != nil {
		return nil, err
	}
	set := make(map[string]struct{})
	for _, r := range rows {
		set[r.IP] = struct{}{}
	}
	return set, nil
}

func (r *DeviceIPRepo) AddIP(uid uint, dev, ip string) error {
	return r.db.Create(&models.UserDeviceIP{
		UserID: uid, Device: dev, IP: ip, CreatedAt: time.Now(),
	}).Error
}

func (r *DeviceIPRepo) TrimOldest(uid uint, dev string) error {
	return r.db.Exec(`
		DELETE FROM user_device_ips
		WHERE id IN (
			SELECT id FROM user_device_ips
			WHERE user_id = ? AND device = ?
			ORDER BY created_at ASC
			LIMIT (
				SELECT GREATEST(COUNT(*) - 3,0)
				FROM user_device_ips
				WHERE user_id = ? AND device = ?
			)
		)
	`, uid, dev, uid, dev).Error
}
