package models

import (
	"errors"
	"time"

	"github.com/duo-labs/webauthn/webauthn"
	"gorm.io/gorm"
)





const (
	TipoDocumentoDNI = "DNI"
	TipoDocumentoCE  = "CE"
	TipoDocumentoNinguno = "NINGUNO"
)

type Usuarios struct {
		ID                uint                  `gorm:"primaryKey;autoIncrement;column:idusuario" json:"id"`
	Nombre_Apellidos   string                `gorm:"size:100;not null;index" json:"nombre_apellidos"`
	FechaNacimiento   time.Time             `gorm:"type:date;not null" json:"fecha_nacimiento"`
	Tipo_Documento     string                `gorm:"size:50;not null;index" json:"tipo_documento"`
	Numero_Documento   string                `gorm:"size:50;uniqueIndex;not null" json:"numero_documento"`
	Sexo              string                `gorm:"size:20;not null" json:"sexo"`
	Celular           string                `gorm:"size:20;not null;index" json:"celular"`
	Correo            string                `gorm:"size:100;uniqueIndex;not null" json:"correo"`
	Activo            bool                  `gorm:"default:false;index" json:"activo"`
	Foto              string                `gorm:"type:mediumtext" json:"foto"`
	NombreUsuario     string                `gorm:"size:50;uniqueIndex;not null" json:"nombre_usuario"`
	Contrasena        string                `gorm:"size:255;not null" json:"-"`
	Intentos          int                   `gorm:"default:0" json:"intentos"`
	CreatedAt         time.Time             `gorm:"index" json:"created_at,omitempty"`
	UpdatedAt         time.Time             `json:"updated_at,omitempty"`
	PasswordExpiresAt time.Time             `gorm:"index" json:"password_expires_at"`
	ActivationToken   string                `gorm:"size:255;index"`
	ActivationExpiry  time.Time             `gorm:"index"`
	LastLoginAt       *time.Time            `gorm:"index" json:"last_login_at,omitempty"`
	LastLoginIP       string                `gorm:"size:45" json:"-"`
	LastUserAgent     string                `gorm:"size:255" json:"-"`
	PasswordChangedAt *time.Time            `json:"-"`
	Roles             []Role                `gorm:"many2many:user_roles;foreignKey:ID;joinForeignKey:usuarios_id_usuario;references:ID;joinReferences:roles_id" json:"roles"`
	BiometricCreds    []BiometricCredential `gorm:"foreignKey:UserID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"-"`
	RoleIDs           []uint                `gorm:"-" json:"role_ids,omitempty"`
}




func (UserRole) TableName() string                 { return "user_roles" }
func (u *Usuarios) TableName() string              { return "usuarios" }
func (u *Usuarios) WebAuthnID() []byte             { return []byte(u.NombreUsuario) }
func (u *Usuarios) WebAuthnName() string           { return u.Nombre_Apellidos }
func (u *Usuarios) WebAuthnDisplayName() string    { return u.Nombre_Apellidos }
func (u *Usuarios) WebAuthnIcon() string           { return "" }

func (u *Usuarios) WebAuthnCredentials() []webauthn.Credential {
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

func (u *Usuarios) ValidateTipoDocumento() error {
	switch u.Tipo_Documento {
	case TipoDocumentoDNI, TipoDocumentoCE, TipoDocumentoNinguno:
		return nil
	default:
		return errors.New("tipo de documento inválido")
	}
}

func (u *Usuarios) BeforeSave(tx *gorm.DB) error {
	return u.ValidateTipoDocumento()
}