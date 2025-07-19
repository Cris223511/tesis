package models

import (
    "database/sql/driver"
    "fmt"
    "strings"
    "time"
)

const dateLayout = "2006-01-02"

// Date admite solo el formato YYYY-MM-DD
type Date struct {
    time.Time
}

// UnmarshalJSON recibe cadenas "YYYY-MM-DD"
func (d *Date) UnmarshalJSON(b []byte) error {
    s := strings.Trim(string(b), `"`)
    if s == "" {
        return nil
    }
    t, err := time.Parse(dateLayout, s)
    if err != nil {
        return err
    }
    d.Time = t
    return nil
}

// MarshalJSON emite cadenas "YYYY-MM-DD"
func (d Date) MarshalJSON() ([]byte, error) {
    return []byte(fmt.Sprintf(`"%s"`, d.Time.Format(dateLayout))), nil
}

// Value convierte Date al valor SQL (cadena YYYY-MM-DD)
func (d Date) Value() (driver.Value, error) {
    if d.IsZero() {
        return nil, nil
    }
    return d.Time.Format(dateLayout), nil
}

// Scan lee valores DATE o cadena desde la base
func (d *Date) Scan(src interface{}) error {
    if src == nil {
        return nil
    }
    switch v := src.(type) {
    case time.Time:
        d.Time = v
        return nil
    case []byte:
        t, err := time.Parse(dateLayout, string(v))
        if err != nil {
            return err
        }
        d.Time = t
        return nil
    case string:
        t, err := time.Parse(dateLayout, v)
        if err != nil {
            return err
        }
        d.Time = t
        return nil
    }
    return fmt.Errorf("no se puede convertir %T en Date", src)
}
