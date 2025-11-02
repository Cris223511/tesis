package utils

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"
	"log"
)


type MLServiceClient struct {
	BaseURL string
	Client  *http.Client
	JWTToken string
}


func NewMLServiceClient() *MLServiceClient {
	baseURL := os.Getenv("ML_SERVICE_URL")
	if baseURL == "" {
		log.Printf("⚠️ Warning: ML_SERVICE_URL no configurada en variables de entorno")
	}

	return &MLServiceClient{
		BaseURL: baseURL,
		Client: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}


func (c *MLServiceClient) SetJWTToken(token string) {
	c.JWTToken = token
}


// EmotionAnalysis análisis emocional individual
type EmotionAnalysis struct {
	ID                   int                    `json:"analysis_id"`
	EmotionName          string                 `json:"emotion_name"`
	Confidence           float64                `json:"confidence"`
	RawConfidence        float64                `json:"raw_confidence"`
	QualityStatus        string                 `json:"quality_status"`
	SessionDate          time.Time              `json:"session_date"`
	PacienteID           int                    `json:"paciente_id"`
	TherapySessionID     *int                   `json:"therapy_session_id"`
	ResponsableUserID    *int                   `json:"responsable_user_id"`
	ProcessingTimeMs     *float64               `json:"processing_time_ms"`
	AlgorithmVersion     string                 `json:"algorithm_version"`
	SessionNotes         string                 `json:"session_notes"`
	SessionType          string                 `json:"session_type"`
	AnalysisMetadata     map[string]interface{} `json:"analysis_metadata"`
	AllEmotionsData      map[string]interface{} `json:"all_emotions_data"`
	MeetsPrecisionTarget bool                   `json:"meets_precision_target"`
}

// PatientEmotionHistory historial completo de emociones de un paciente
type PatientEmotionHistory struct {
	TotalAnalyses       int               `json:"total_analyses"`
	AverageConfidence   float64           `json:"average_confidence"`
	MostFrequentEmotion string            `json:"most_frequent_emotion"`
	EmotionDistribution map[string]int    `json:"emotion_distribution"`
	ConfidenceTrend     string            `json:"confidence_trend"`
	Analyses            []EmotionAnalysis `json:"analyses"`
	DateRange           string            `json:"date_range"`
	GeneratedAt         time.Time         `json:"generated_at"`
}


type EmotionReportGeneration struct {
	PatientID     int                    `json:"patient_id"`
	UserID        int                    `json:"user_id"`
	DateFrom      *time.Time             `json:"date_from,omitempty"`
	DateTo        *time.Time             `json:"date_to,omitempty"`
	ReportFormat  string                 `json:"report_format"` 
	IncludeSessions bool                 `json:"include_sessions"`
	Metadata      map[string]interface{} `json:"metadata,omitempty"`
}


type ReportGenerationResponse struct {
	Success         bool                   `json:"success"`
	ReportID        string                 `json:"report_id"`
	ReportPath      string                 `json:"report_path"`
	ReportSize      int64                  `json:"report_size"`
	ReportFormat    string                 `json:"report_format"`
	GeneratedAt     time.Time              `json:"generated_at"`
	EmotionSummary  PatientEmotionHistory  `json:"emotion_summary"`
	Message         string                 `json:"message"`
	Metadata        map[string]interface{} `json:"metadata,omitempty"`
}


func (c *MLServiceClient) makeRequest(method, endpoint string, body interface{}) (*http.Response, error) {
	var reqBody io.Reader

	if body != nil {
		jsonData, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("error marshaling request body: %w", err)
		}
		reqBody = bytes.NewBuffer(jsonData)
	}

	url := fmt.Sprintf("%s%s", c.BaseURL, endpoint)
	req, err := http.NewRequest(method, url, reqBody)
	if err != nil {
		return nil, fmt.Errorf("error creating request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if c.JWTToken != "" {
		req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", c.JWTToken))
	}

	log.Printf("🌐 ML Service Request: %s %s", method, url)

	resp, err := c.Client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("error making request: %w", err)
	}

	return resp, nil
}


func (c *MLServiceClient) GetPatientEmotionHistory(patientID int, dateFrom, dateTo *time.Time, limit int) (*PatientEmotionHistory, error) {
	endpoint := fmt.Sprintf("/api/v1/emotion/patient/%d/history", patientID)
	params := fmt.Sprintf("?limit=%d", limit)
	if dateFrom != nil {
		params += fmt.Sprintf("&date_from=%s", dateFrom.Format("2006-01-02T15:04:05"))
	}
	if dateTo != nil {
		params += fmt.Sprintf("&date_to=%s", dateTo.Format("2006-01-02T15:04:05"))
	}

	endpoint += params

	resp, err := c.makeRequest("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("ML service error: %s (status: %d)", string(body), resp.StatusCode)
	}

	var response struct {
		Data PatientEmotionHistory `json:"data"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, fmt.Errorf("error decoding response: %w", err)
	}

	return &response.Data, nil
}

func (c *MLServiceClient) GetSessionEmotionAnalysis(sessionID int) ([]EmotionAnalysis, error) {
	endpoint := fmt.Sprintf("/api/v1/emotion/session/%d/analyses", sessionID)

	resp, err := c.makeRequest("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("ML service error: %s (status: %d)", string(body), resp.StatusCode)
	}

	var response struct {
		Data []EmotionAnalysis `json:"data"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, fmt.Errorf("error decoding response: %w", err)
	}

	return response.Data, nil
}

func (c *MLServiceClient) GenerateEmotionReport(request EmotionReportGeneration) (*ReportGenerationResponse, error) {
	endpoint := "/api/v1/emotion/reports/generate"

	resp, err := c.makeRequest("POST", endpoint, request)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("ML service error: %s (status: %d)", string(body), resp.StatusCode)
	}

	var response ReportGenerationResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, fmt.Errorf("error decoding response: %w", err)
	}

	return &response, nil
}


func (c *MLServiceClient) GetPatientEmotionStats(patientID int, dateFrom, dateTo *time.Time) (map[string]interface{}, error) {
	endpoint := fmt.Sprintf("/api/v1/emotion/patient/%d/stats", patientID)
	params := ""
	if dateFrom != nil {
		params += fmt.Sprintf("?date_from=%s", dateFrom.Format("2006-01-02T15:04:05"))
	}
	if dateTo != nil {
		if params == "" {
			params += "?"
		} else {
			params += "&"
		}
		params += fmt.Sprintf("date_to=%s", dateTo.Format("2006-01-02T15:04:05"))
	}

	endpoint += params

	resp, err := c.makeRequest("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("ML service error: %s (status: %d)", string(body), resp.StatusCode)
	}

	var response struct {
		Data map[string]interface{} `json:"data"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, fmt.Errorf("error decoding response: %w", err)
	}

	return response.Data, nil
}
func (c *MLServiceClient) CheckMLServiceHealth() error {
	resp, err := c.makeRequest("GET", "/health", nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("ML service unhealthy: status %d", resp.StatusCode)
	}

	return nil
}

// NotifySessionCompletion notifica al ML service que una sesión se completó
func (c *MLServiceClient) NotifySessionCompletion(sessionID int, patientID int, metadata map[string]interface{}) error {
	request := map[string]interface{}{
		"session_id":  sessionID,
		"patient_id":  patientID,
		"status":      "completed",
		"metadata":    metadata,
		"updated_at":  time.Now().Format(time.RFC3339),
	}

	endpoint := "/api/v1/sessions/update-status"
	resp, err := c.makeRequest("POST", endpoint, request)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		log.Printf("⚠️ Warning: Failed to notify ML service about session completion: %s", string(body))
	}

	return nil
}