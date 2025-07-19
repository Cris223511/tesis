package models

import (
	"errors"
	"time"

	"github.com/duo-labs/webauthn/webauthn"
)





const (
	TipoDocumentoDNI = "DNI"
	TipoDocumentoCE  = "CE"
	TipoDocumentoNinguno = "NINGUNO"
)

type User struct {
	ID                uint      `gorm:"primaryKey"`
	Nombre_Apellidos  string    `gorm:"type:varchar(100);not null" json:"nombre_apellidos"`
	FechaNacimiento   time.Time `gorm:"type:date;not null" json:"fecha_nacimiento"`
	Tipo_Documento    string    `gorm:"type:varchar(50);not null" json:"tipo_documento"`
	Numero_Documento  string    `gorm:"type:varchar(50);unique;not null" json:"numero_documento"`
	Sexo              string    `gorm:"type:varchar(20);not null" json:"sexo"`
	Celular           string    `gorm:"type:varchar(20);not null" json:"celular"`
	Correo            string    `gorm:"type:varchar(100);unique;not null" json:"correo"`
	Activo            bool      `gorm:"default:false" json:"activo"`
	Foto              string    `gorm:"type:mediumtext" json:"foto"`
	NombreUsuario     string    `gorm:"type:varchar(50);unique;not null" json:"nombre_usuario"`
	Contrasena        string    `gorm:"type:varchar(255);not null" json:"password"`
	RoleIDs           []uint    `gorm:"-" json:"role_ids"`
	Roles             []Role    `gorm:"many2many:user_roles;" json:"roles"`
	Intentos          int       `gorm:"default:0" json:"intentos"`
	CreatedAt         time.Time `json:"created_at,omitempty"`
	UpdatedAt         time.Time `json:"updated_at,omitempty"`
	PasswordExpiresAt time.Time `json:"password_expires_at"`
	ActivationToken   string    `gorm:"type:varchar(255);index"`
	ActivationExpiry  time.Time `gorm:"index"`
	LastLoginAt       *time.Time `json:"last_login_at,omitempty"`
	LastLoginIP       string    `gorm:"type:varchar(45)" json:"-"`
	LastUserAgent     string    `gorm:"type:varchar(255)" json:"-"`
	PasswordChangedAt *time.Time `json:"-"`
	BiometricCreds    []BiometricCredential `gorm:"foreignKey:UserID"`
}

func (u *User) WebAuthnID() []byte          { return []byte(u.NombreUsuario) }
func (u *User) WebAuthnName() string        { return u.Nombre_Apellidos }
func (u *User) WebAuthnDisplayName() string { return u.Nombre_Apellidos }
func (u *User) WebAuthnIcon() string        { return "" }

func (u *User) WebAuthnCredentials() []webauthn.Credential {
	cs := make([]webauthn.Credential, len(u.BiometricCreds))
	for i, c := range u.BiometricCreds {
		cs[i] = webauthn.Credential{
			ID:        c.CredentialID,
			PublicKey: c.PublicKey,
			Authenticator: webauthn.Authenticator{
				SignCount: c.SignCount,
			},
		}
	}
	return cs
}

func (u *User) ValidateTipoDocumento() error {
	switch u.Tipo_Documento {
	case TipoDocumentoDNI, TipoDocumentoCE, TipoDocumentoNinguno:
		return nil
	default:
		return errors.New("tipo de documento inválido")
	}
}

func (u *User) BeforeSave() error {
	return u.ValidateTipoDocumento()
}