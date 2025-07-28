package utils

import (
	"encoding/base64"
	"errors"
	"fmt"

	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"
)


func ParseID(idStr string) (uint, error) {
	id, err := strconv.ParseUint(idStr, 10, 32)
	if err != nil {
		return 0, errors.New("ID inválido, debe ser un número")
	}
	return uint(id), nil
}





func DecodeBase64File(encoded string) ([]byte, error) {

    decoded, err := base64.StdEncoding.DecodeString(encoded)

    if err != nil {

        return nil, errors.New("error decoding base64 string")

    }

    return decoded, nil

}



func HashPassword(password string) (string, error) {
	bytes, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	return string(bytes), err
}


func CheckPasswordHash(password, hash string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(password))
	return err == nil
}

func SaveBase64File(base64Str, originalName, folder, prefix string) (string, error) {
    parts := strings.Split(base64Str, ";base64,")
    if len(parts) == 2 {
        base64Str = parts[1]
    }
    data, err := base64.StdEncoding.DecodeString(base64Str)
    if err != nil {
        return "", err
    }
    ext := filepath.Ext(originalName)
    name := fmt.Sprintf("%s_%d%s", prefix, time.Now().UnixNano(), ext)
    fullPath := filepath.Join(folder, name)

    os.MkdirAll(folder, 0755)
    if err := os.WriteFile(fullPath, data, 0644); err != nil {
        return "", err
    }

    return fullPath, nil
}


func ParseMultipleFormats(dateStr string) (time.Time, error) {
    formats := []string{
        "2006-01-02 15:04:05",
        "2006-01-02",
        time.RFC3339,
        "2006-01-02T15:04:05",
        "02/01/2006",
        "2006/01/02",
    }

    var parseErr error
    for _, format := range formats {
        parsedTime, err := time.Parse(format, dateStr)
        if err == nil {
            return parsedTime, nil
        }
        parseErr = err
    }

    return time.Time{}, parseErr
}


