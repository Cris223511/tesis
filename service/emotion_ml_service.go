package services

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"time"
	"usuarios/utils"
)

type EmotionMLService struct {
	baseURL    string
	httpClient *http.Client
}

type EmotionAnalysis struct {
	ID              string    `json:"id"`
	UserID          uint      `json:"user_id"`
	ChildID         *uint     `json:"child_id,omitempty"`
	SessionID       *uint     `json:"session_id,omitempty"`
	DominantEmotion string    `json:"dominant_emotion"`
	ConfidenceScore float64   `json:"confidence_score"`
	CreatedAt       time.Time `json:"created_at"`
}

type sessionAnalysesResponse struct {
	Analyses []EmotionAnalysis `json:"analyses"`
}

func NewEmotionMLService() *EmotionMLService {
	baseURL := os.Getenv("ML_SERVICE_URL")
	if baseURL == "" {
		baseURL = "http://localhost:5001"
	}

	return &EmotionMLService{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 20 * time.Second,
		},
	}
}

func (s *EmotionMLService) GetSessionAnalysis(sessionID uint) (*EmotionAnalysis, error) {
	sessionIDs := []uint{sessionID}
	analyses, err := s.GetSessionAnalyses(sessionIDs)
	if err != nil {
		return nil, err
	}
	analysis, exists := analyses[sessionID]
	if !exists {
		return nil, nil
	}
	return &analysis, nil
}

func (s *EmotionMLService) GetSessionAnalyses(sessionIDs []uint) (map[uint]EmotionAnalysis, error) {
	if len(sessionIDs) == 0 {
		return map[uint]EmotionAnalysis{}, nil
	}

	payload := struct {
		SessionIDs []uint `json:"session_ids"`
	}{
		SessionIDs: sessionIDs,
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequest(http.MethodPost, s.baseURL+"/api/v1/sessions/analyses", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}

	token, err := utils.GenerateServiceToken(1, "AD", 10*time.Minute)
	if err != nil {
		return nil, err
	}

	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("ml-service respondio con estado %d", resp.StatusCode)
	}

	var response sessionAnalysesResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, err
	}

	result := make(map[uint]EmotionAnalysis, len(response.Analyses))
	for _, analysis := range response.Analyses {
		if analysis.SessionID != nil {
			result[*analysis.SessionID] = analysis
		}
	}

	return result, nil
}
