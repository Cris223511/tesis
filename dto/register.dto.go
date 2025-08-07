package dto

import "time"


type RegisterRequest struct {
    NombresApellidos string `json:"nombres_apellidos" binding:"required"`
      FechaNacimiento FechaNacimiento `json:"fecha_nacimiento"`
    TipoDocumento   string `json:"tipo_documento" binding:"required"`
    NumDocumento    string `json:"num_documento"`
    Sexo            string `json:"sexo" binding:"required"`
    Telefono        string `json:"telefono" binding:"required"`
    Correo          string `json:"correo" binding:"required,email"`
    RoleIDs         []uint `json:"role_ids" binding:"required"`
}


type FechaNacimiento time.Time

func (f FechaNacimiento) MarshalJSON() ([]byte, error) {
  s := time.Time(f).Format(`"2006-01-02"`)
  return []byte(s), nil
}