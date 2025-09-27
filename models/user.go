package models

import (
	"database/sql/driver"
	"errors"
	"strings"
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
    ID                 uint                  `gorm:"primaryKey;autoIncrement;column:idusuario" json:"id"`
    Nombres_Apellidos  string     `gorm:"column:nombres_apellidos;size:100;not null;index" json:"nombres_apellidos"`
   FechaNacimiento FechaNacimiento `json:"fecha_nacimiento"`
    Tipo_Documento     string                `gorm:"size:50;not null;index" json:"tipo_documento"`
    Num_Documento      string                `gorm:"size:50;uniqueIndex;not null" json:"num_documento"`
    Descripcion        string                `gorm:"type:text" json:"descripcion"`
    Sexo               string                `gorm:"size:20;not null" json:"sexo"`
    Telefono           string                `gorm:"size:20;not null;index" json:"telefono"`
    Correo             string                `gorm:"size:100;uniqueIndex;not null" json:"correo"`
    Activo             bool                  `gorm:"default:false;index" json:"activo"`
    FotoMovil          string                `gorm:"type:mediumtext;column:foto_movil" json:"foto_movil"`
    BannerMovil        string                `gorm:"type:mediumtext;column:banner_movil" json:"banner_movil"`
    Usuario            string                `gorm:"size:50;uniqueIndex;not null" json:"usuario"`
    Contrasena         string                `gorm:"size:255;not null" json:"-"`
    Intentos           int                   `gorm:"default:0" json:"intentos"`
    DobleFactor        bool                  `gorm:"column:doble_factor;default:false" json:"doble_factor"` 
    CreatedAt          time.Time             `gorm:"index" json:"created_at,omitempty"`
    UpdatedAt          time.Time             `json:"updated_at,omitempty"`
    PasswordExpiresAt  time.Time             `gorm:"index" json:"password_expires_at"`
    ActivationToken    string                `gorm:"size:255;index" json:"-"`
    ActivationExpiry   time.Time             `gorm:"index" json:"-"`
    LastLoginAt        *time.Time            `gorm:"index" json:"last_login_at,omitempty"`
    LastLoginIP        string                `gorm:"size:45" json:"-"`
    LastUserAgent      string                `gorm:"size:255" json:"-"`
    PasswordChangedAt  *time.Time            `json:"-"`
    Roles []Role `gorm:"many2many:user_roles;foreignKey:ID;joinForeignKey:usuarios_id_usuario;references:ID;joinReferences:roles_id" json:"roles"`
    BiometricCreds     []BiometricCredential `gorm:"foreignKey:UserID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"-"`
    RoleIDs            []uint                `gorm:"-" json:"role_ids,omitempty"`
}


func (UserRole) TableName() string                 { return "user_roles" }
func (u *Usuarios) TableName() string              { return "usuarios" }
func (u *Usuarios) WebAuthnID() []byte             { return []byte(u.Usuario) }
func (u *Usuarios) WebAuthnName() string           { return u.Nombres_Apellidos }
func (u *Usuarios) WebAuthnDisplayName() string    { return u.Nombres_Apellidos }
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

type FechaNacimiento time.Time

func (f FechaNacimiento) MarshalJSON() ([]byte, error) {
  s := time.Time(f).Format(`"2006-01-02"`)
  return []byte(s), nil
}

func (f *FechaNacimiento) UnmarshalJSON(data []byte) error {
	str := string(data)
	str = strings.Trim(str, `"`)
	t, err := time.Parse("2006-01-02", str)
	if err != nil {
		return err
	}
	*f = FechaNacimiento(t)
	return nil
}

// Scan implements the Scanner interface for database/sql
func (f *FechaNacimiento) Scan(value interface{}) error {
	if value == nil {
		return nil
	}

	switch v := value.(type) {
	case time.Time:
		*f = FechaNacimiento(v)
	case []byte:
		t, err := time.Parse("2006-01-02 15:04:05", string(v))
		if err != nil {
			return err
		}
		*f = FechaNacimiento(t)
	case string:
		t, err := time.Parse("2006-01-02 15:04:05", v)
		if err != nil {
			return err
		}
		*f = FechaNacimiento(t)
	default:
		return errors.New("cannot scan FechaNacimiento")
	}
	return nil
}

// Value implements the driver Valuer interface
func (f FechaNacimiento) Value() (driver.Value, error) {
	return time.Time(f), nil
}