package utils

import (
	"encoding/json"
	"strconv"
)

type AuthorID uint

func (a *AuthorID) UnmarshalJSON(data []byte) error {
	// Intentar deserializar como número
	var num uint64
	if err := json.Unmarshal(data, &num); err == nil {
		*a = AuthorID(num)
		return nil
	}

	var s string
	if err := json.Unmarshal(data, &s); err != nil {
		return err
	}
	parsed, err := strconv.ParseUint(s, 10, 64)
	if err != nil {
		return err
	}
	*a = AuthorID(parsed)
	return nil
}

func (a AuthorID) MarshalJSON() ([]byte, error) {
	return json.Marshal(strconv.FormatUint(uint64(a), 10))
}
