package optimizations

import (
	"database/sql"
	"time"

	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

type DBConfig struct {
	DSN             string
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxLifetime time.Duration
	ConnMaxIdleTime time.Duration
}

func NewOptimizedDB(config DBConfig) (*gorm.DB, error) {
	db, err := gorm.Open(mysql.Open(config.DSN), &gorm.Config{
		Logger:                 logger.Default.LogMode(logger.Silent),
		PrepareStmt:            true,
		SkipDefaultTransaction: true,
		DisableForeignKeyConstraintWhenMigrating: true,
	})

	if err != nil {
		return nil, err
	}

	sqlDB, err := db.DB()
	if err != nil {
		return nil, err
	}

	sqlDB.SetMaxOpenConns(config.MaxOpenConns)
	sqlDB.SetMaxIdleConns(config.MaxIdleConns)
	sqlDB.SetConnMaxLifetime(config.ConnMaxLifetime)
	sqlDB.SetConnMaxIdleTime(config.ConnMaxIdleTime)

	return db, nil
}

func OptimizeQueries(db *gorm.DB) {
	db.Exec("SET GLOBAL query_cache_type = 1")
	db.Exec("SET GLOBAL query_cache_size = 67108864")
	db.Exec("SET GLOBAL tmp_table_size = 67108864")
	db.Exec("SET GLOBAL max_heap_table_size = 67108864")
	db.Exec("SET GLOBAL join_buffer_size = 262144")
	db.Exec("SET GLOBAL sort_buffer_size = 262144")
}

func CreateIndexes(db *gorm.DB) {
	db.Exec("CREATE INDEX idx_usuarios_correo ON usuarios(correo)")
	db.Exec("CREATE INDEX idx_usuarios_tipo_documento ON usuarios(tipo_documento, numero_documento)")
	db.Exec("CREATE INDEX idx_therapy_sessions_estado ON therapy_sessions(estado)")
	db.Exec("CREATE INDEX idx_therapy_sessions_fecha ON therapy_sessions(fecha_hora)")
	db.Exec("CREATE INDEX idx_patients_therapist ON patients(therapist_id)")
	db.Exec("CREATE INDEX idx_therapist_ratings_session ON therapist_ratings(session_id)")
	db.Exec("CREATE INDEX idx_user_roles_composite ON user_roles(usuarios_id, role_id)")
}

func EnableQueryCache(db *sql.DB) {
	db.SetConnMaxLifetime(time.Minute * 5)
	db.SetMaxIdleConns(25)
	db.SetMaxOpenConns(25)
}

type QueryOptimizer struct {
	db *gorm.DB
}

func NewQueryOptimizer(db *gorm.DB) *QueryOptimizer {
	return &QueryOptimizer{db: db}
}

func (qo *QueryOptimizer) PreloadOptimized(query *gorm.DB, associations ...string) *gorm.DB {
	for _, assoc := range associations {
		query = query.Preload(assoc)
	}
	return query
}

func (qo *QueryOptimizer) BatchFind(table string, ids []uint, batchSize int) []map[string]interface{} {
	var results []map[string]interface{}

	for i := 0; i < len(ids); i += batchSize {
		end := i + batchSize
		if end > len(ids) {
			end = len(ids)
		}

		var batch []map[string]interface{}
		qo.db.Table(table).Where("id IN ?", ids[i:end]).Find(&batch)
		results = append(results, batch...)
	}

	return results
}

func (qo *QueryOptimizer) SelectFields(query *gorm.DB, fields []string) *gorm.DB {
	return query.Select(fields)
}