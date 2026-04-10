SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;


CREATE TABLE `banner_changes` (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `changed_at` datetime(3) NOT NULL,
  `banner_data` mediumtext NOT NULL,
  `created_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `biometric_credentials` (
  `id` bigint UNSIGNED NOT NULL,
  `usuarios_id_usuario` bigint UNSIGNED NOT NULL,
  `credential_id` varbinary(255) NOT NULL,
  `public_key` longblob NOT NULL,
  `sign_count` int UNSIGNED NOT NULL,
  `transports` longtext,
  `device_name` varchar(100) DEFAULT NULL,
  `device_type` varchar(50) DEFAULT NULL,
  `last_used` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `blogs` (
  `idblog` int NOT NULL,
  `idusuario` int NOT NULL,
  `titulo` varchar(100) NOT NULL,
  `descripcion` longtext NOT NULL,
  `imagen` varchar(100) NOT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'desactivado',
  `fecha_creacion` datetime DEFAULT NULL,
  `fecha_actualizacion` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `emociones` (
  `idemocion` int NOT NULL,
  `nombre` varchar(30) DEFAULT NULL,
  `descripcion` text,
  `color` varchar(7) DEFAULT '#000000',
  `activo` tinyint(1) DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `emociones_detectadas` (
  `idemocion_detectada` int NOT NULL,
  `paciente_id` int DEFAULT NULL,
  `idemocion` int DEFAULT NULL,
  `porcentaje` decimal(11,2) DEFAULT NULL,
  `responsable_user_id` int DEFAULT NULL,
  `therapy_session_id` int DEFAULT NULL,
  `confidence_percentage` float DEFAULT '0',
  `raw_confidence_percentage` float DEFAULT '0',
  `all_emotions_data` json DEFAULT NULL,
  `processing_time_ms` float DEFAULT NULL,
  `algorithm_version` varchar(50) DEFAULT '7.0_DeepFace',
  `meets_precision_target` tinyint(1) DEFAULT '0',
  `quality_status` varchar(20) DEFAULT 'MODERATE',
  `session_notes` text,
  `session_type` varchar(20) DEFAULT 'THERAPEUTIC',
  `image_path` varchar(500) DEFAULT NULL,
  `analysis_metadata` json DEFAULT NULL,
  `session_date` datetime DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `enlaces_sesion_emocion` (
  `idenlace_sesion_emocion` int NOT NULL,
  `idsesion_terapeutica` int NOT NULL,
  `idanalisis_emocion` int NOT NULL,
  `momento_analisis` enum('PRE_SESSION','MID_SESSION','POST_SESSION') NOT NULL,
  `fecha_creacion` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `login_histories` (
  `id` bigint UNSIGNED NOT NULL,
  `usuarios_id_usuario` bigint UNSIGNED NOT NULL,
  `ip_address` varchar(45) NOT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `login_type` varchar(20) DEFAULT NULL,
  `success` tinyint(1) DEFAULT '1',
  `fail_reason` varchar(100) DEFAULT NULL,
  `location` varchar(100) DEFAULT NULL,
  `device_info` text,
  `created_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `mediciones` (
  `idmedicion` int NOT NULL,
  `idreporte` int DEFAULT NULL,
  `nivel_estres` decimal(11,2) DEFAULT NULL,
  `nivel_atencion` decimal(11,2) DEFAULT NULL,
  `nivel_ansiedad` decimal(11,2) DEFAULT NULL,
  `nivel_emocional_general` decimal(11,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `otps` (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED DEFAULT NULL,
  `code` varchar(6) DEFAULT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `is_used` tinyint(1) DEFAULT '0',
  `attempts` bigint DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  `encrypted_code` varchar(255) DEFAULT NULL,
  `code_hash` varchar(64) DEFAULT NULL,
  `salt` varchar(32) DEFAULT NULL,
  `client_ip` varchar(45) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `purpose` varchar(50) DEFAULT 'login'
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `otp_resends` (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED DEFAULT NULL,
  `resend_count` bigint DEFAULT '0',
  `last_resend_at` datetime(3) DEFAULT NULL,
  `blocked_until` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `password_histories` (
  `id` bigint UNSIGNED NOT NULL,
  `usuarios_id_usuario` bigint UNSIGNED NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `changed_by` bigint UNSIGNED DEFAULT NULL,
  `change_reason` varchar(100) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `patients` (
  `id` bigint UNSIGNED NOT NULL,
  `nombres_apellidos` varchar(100) NOT NULL,
  `fecha_nacimiento` datetime(3) DEFAULT NULL,
  `tipo_documento` varchar(20) NOT NULL,
  `num_documento` varchar(50) NOT NULL,
  `altura` decimal(5,2) DEFAULT NULL,
  `peso` decimal(5,2) DEFAULT NULL,
  `imc` decimal(5,2) DEFAULT NULL,
  `sexo` varchar(20) NOT NULL,
  `diagnostico_clinico` text,
  `foto_web` mediumtext,
  `foto_movil` mediumtext,
  `serial_id` varchar(13) NOT NULL DEFAULT '',
  `terapeuta_id` bigint UNSIGNED NOT NULL,
  `responsable_id` bigint UNSIGNED DEFAULT NULL,
  `activo` tinyint(1) DEFAULT '1',
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `cuidador_id` bigint UNSIGNED DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `photo_changes` (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `changed_at` datetime(3) NOT NULL,
  `photo_data` mediumtext NOT NULL,
  `created_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `profile_changes` (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `changed_at` datetime(3) NOT NULL,
  `old_email` varchar(100) DEFAULT NULL,
  `new_email` varchar(100) DEFAULT NULL,
  `old_phone` varchar(20) DEFAULT NULL,
  `new_phone` varchar(20) DEFAULT NULL,
  `change_type` varchar(50) NOT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `reportes` (
  `idreporte` int NOT NULL,
  `idpaciente` int DEFAULT NULL,
  `fecha_hora` datetime DEFAULT NULL,
  `descripcion` text,
  `total_emociones_detectadas` int DEFAULT NULL,
  `emocion_predominante` varchar(30) DEFAULT NULL,
  `porcentaje_predominante` decimal(11,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `reportes_analisis_emocional` (
  `idreporte_analisis` int NOT NULL,
  `idpaciente` int NOT NULL,
  `generado_por_usuario_id` int NOT NULL,
  `tipo_reporte` enum('INDIVIDUAL_SESSION','PATIENT_HISTORY','COMPARATIVE_ANALYSIS','PROGRESS_SUMMARY') NOT NULL,
  `fecha_desde` datetime NOT NULL,
  `fecha_hasta` datetime NOT NULL,
  `titulo_reporte` varchar(200) NOT NULL,
  `resumen_reporte` text,
  `ruta_archivo_reporte` varchar(500) DEFAULT NULL,
  `formato_reporte` varchar(10) DEFAULT 'PDF',
  `estado_generacion` enum('PENDING','GENERATING','COMPLETED','FAILED') DEFAULT 'PENDING',
  `total_sesiones_analizadas` int DEFAULT '0',
  `confianza_promedio` float DEFAULT NULL,
  `emocion_mas_frecuente` varchar(50) DEFAULT NULL,
  `fecha_creacion` datetime DEFAULT CURRENT_TIMESTAMP,
  `fecha_completado` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `report_history` (
  `id` bigint UNSIGNED NOT NULL,
  `paciente_id` bigint UNSIGNED NOT NULL,
  `generated_by_user_id` bigint UNSIGNED NOT NULL,
  `report_type` varchar(50) NOT NULL,
  `session_id` bigint UNSIGNED DEFAULT NULL,
  `report_title` varchar(200) NOT NULL,
  `report_description` text,
  `date_range` varchar(100) DEFAULT NULL,
  `total_sessions` bigint DEFAULT '0',
  `completed_sessions` bigint DEFAULT '0',
  `average_confidence` decimal(5,2) DEFAULT '0.00',
  `dominant_emotion` varchar(50) DEFAULT NULL,
  `emotion_trend` varchar(20) DEFAULT NULL,
  `current_therapist_id` bigint UNSIGNED NOT NULL,
  `therapist_changed` tinyint(1) DEFAULT '0',
  `previous_therapist_id` bigint UNSIGNED DEFAULT NULL,
  `file_path` varchar(500) DEFAULT NULL,
  `file_format` varchar(10) NOT NULL,
  `file_size_bytes` bigint DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  `pdf_content` longtext
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `resumen_estadisticas_emociones` (
  `idresumen_estadisticas` int NOT NULL,
  `idpaciente` int NOT NULL,
  `anio` int NOT NULL,
  `mes` int NOT NULL,
  `total_analisis` int DEFAULT '0',
  `contador_alta_confianza` int DEFAULT '0',
  `confianza_promedio` float DEFAULT NULL,
  `emocion_mas_frecuente` varchar(50) DEFAULT NULL,
  `distribucion_emociones` json DEFAULT NULL,
  `fecha_ultimo_analisis` datetime DEFAULT NULL,
  `fecha_creacion` datetime DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `roles` (
  `id` bigint UNSIGNED NOT NULL,
  `name` varchar(50) NOT NULL,
  `updated_count` bigint DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `security_logs` (
  `id` bigint UNSIGNED NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `username` varchar(50) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `details` text,
  `severity` varchar(20) DEFAULT 'info',
  `resolved` tinyint(1) DEFAULT '0',
  `resolved_by` bigint UNSIGNED DEFAULT NULL,
  `resolved_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `therapist_disqualifications` (
  `id` bigint UNSIGNED NOT NULL,
  `therapist_id` bigint UNSIGNED NOT NULL,
  `bad_ratings` bigint DEFAULT '0',
  `is_deleted` tinyint(1) DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `therapist_ratings` (
  `id` bigint UNSIGNED NOT NULL,
  `session_id` bigint UNSIGNED NOT NULL,
  `therapist_id` bigint UNSIGNED NOT NULL,
  `caregiver_id` bigint UNSIGNED NOT NULL,
  `patient_id` bigint UNSIGNED NOT NULL,
  `rating` bigint NOT NULL,
  `comment` text,
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL
) ;

CREATE TABLE `therapy_sessions` (
  `id` bigint UNSIGNED NOT NULL,
  `paciente_id` bigint UNSIGNED NOT NULL,
  `terapeuta_id` bigint UNSIGNED NOT NULL,
  `fecha_sesion` datetime(3) NOT NULL,
  `hora_inicio` varchar(10) NOT NULL,
  `hora_fin` varchar(10) NOT NULL,
  `duracion` bigint NOT NULL,
  `ubicacion` varchar(200) NOT NULL,
  `direccion` text NOT NULL,
  `descripcion` text NOT NULL,
  `objetivos` json NOT NULL,
  `materiales` json NOT NULL,
  `notas_terapeuta` text,
  `estado` varchar(20) DEFAULT 'programada',
  `tipo_sesion` varchar(50) NOT NULL,
  `modalidad` varchar(30) NOT NULL,
  `update_count` bigint DEFAULT '0',
  `is_deleted` tinyint(1) DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  `terapeuta_reasignado_id` bigint UNSIGNED DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `user_device_ips` (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `device` varchar(100) NOT NULL,
  `ip` varchar(45) NOT NULL,
  `created_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `user_relationships` (
  `id` bigint UNSIGNED NOT NULL,
  `parent_id` bigint UNSIGNED NOT NULL,
  `child_id` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `user_roles` (
  `usuarios_id_usuario` bigint UNSIGNED NOT NULL,
  `roles_id` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `user_unblock_cooldowns` (
  `blocker_id` bigint UNSIGNED NOT NULL,
  `unblocked_id` bigint UNSIGNED NOT NULL,
  `unblocked_at` datetime(3) NOT NULL,
  `expire_at` datetime(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `usuarios` (
  `idusuario` int NOT NULL,
  `nombres_apellidos` varchar(100) NOT NULL,
  `usuario` varchar(50) NOT NULL,
  `correo` varchar(100) NOT NULL,
  `contrasena` varchar(255) NOT NULL,
  `tipo_documento` varchar(50) NOT NULL,
  `num_documento` varchar(50) NOT NULL,
  `sexo` varchar(20) NOT NULL,
  `telefono` varchar(20) NOT NULL,
  `fecha_nacimiento` date DEFAULT NULL,
  `rol` varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  `descripcion` text CHARACTER SET utf8 COLLATE utf8_general_ci,
  `token_recover` text,
  `token_recover_date` datetime DEFAULT NULL,
  `doble_factor` tinyint(1) DEFAULT '0',
  `doble_factor_token` text CHARACTER SET utf8 COLLATE utf8_general_ci,
  `date_doble_factor_token` datetime DEFAULT NULL,
  `character_otp` text,
  `character_otp_date` datetime DEFAULT NULL,
  `token_activate_user` text CHARACTER SET utf8 COLLATE utf8_general_ci,
  `token_activate_user_date` datetime DEFAULT NULL,
  `ultimo_sesion_date` datetime DEFAULT NULL,
  `ultimo_sesion_ip` text CHARACTER SET utf8 COLLATE utf8_general_ci,
  `ultimo_agente_user` text CHARACTER SET utf8 COLLATE utf8_general_ci,
  `contrasena_update_date` datetime DEFAULT NULL,
  `contrasena_expires_date` datetime DEFAULT NULL,
  `serial_id` varchar(13) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  `foto_web` longtext CHARACTER SET utf8 COLLATE utf8_general_ci,
  `banner_web` longtext CHARACTER SET utf8 COLLATE utf8_general_ci,
  `foto_movil` mediumtext,
  `banner_movil` mediumtext,
  `intentos` bigint DEFAULT '0',
  `activo` tinyint(1) DEFAULT '0',
  `estado` varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  `fecha_registro` datetime DEFAULT NULL,
  `fecha_actualizacion` datetime DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  `password_expires_at` datetime(3) DEFAULT NULL,
  `activation_token` varchar(255) DEFAULT NULL,
  `activation_expiry` datetime(3) DEFAULT NULL,
  `last_login_at` datetime(3) DEFAULT NULL,
  `last_login_ip` varchar(45) DEFAULT NULL,
  `last_user_agent` varchar(255) DEFAULT NULL,
  `password_changed_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


ALTER TABLE `banner_changes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_banner_changes_user_id` (`user_id`),
  ADD KEY `idx_banner_changes_changed_at` (`changed_at`);

ALTER TABLE `biometric_credentials`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_biometric_credentials_credential_id` (`credential_id`),
  ADD KEY `idx_biometric_credentials_user_id` (`usuarios_id_usuario`);

ALTER TABLE `blogs`
  ADD PRIMARY KEY (`idblog`),
  ADD KEY `idusuario` (`idusuario`);

ALTER TABLE `emociones`
  ADD PRIMARY KEY (`idemocion`),
  ADD KEY `idx_activo` (`activo`);

ALTER TABLE `emociones_detectadas`
  ADD PRIMARY KEY (`idemocion_detectada`),
  ADD KEY `idreporte` (`paciente_id`),
  ADD KEY `idemocion` (`idemocion`),
  ADD KEY `idx_responsable_user_id` (`responsable_user_id`);

ALTER TABLE `enlaces_sesion_emocion`
  ADD PRIMARY KEY (`idenlace_sesion_emocion`),
  ADD KEY `idx_sesion_terapeutica` (`idsesion_terapeutica`),
  ADD KEY `idx_analisis_emocion` (`idanalisis_emocion`),
  ADD KEY `idx_momento_analisis` (`momento_analisis`),
  ADD KEY `idx_fecha_creacion` (`fecha_creacion`);

ALTER TABLE `login_histories`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_login_histories_user_id` (`usuarios_id_usuario`),
  ADD KEY `idx_login_histories_created_at` (`created_at`);

ALTER TABLE `mediciones`
  ADD PRIMARY KEY (`idmedicion`),
  ADD KEY `idreporte` (`idreporte`);

ALTER TABLE `otps`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_otps_user_id` (`user_id`),
  ADD KEY `idx_otps_expires_at` (`expires_at`),
  ADD KEY `idx_otps_is_used` (`is_used`);

ALTER TABLE `otp_resends`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `password_histories`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_password_histories_user_id` (`usuarios_id_usuario`),
  ADD KEY `idx_password_histories_changed_by` (`changed_by`),
  ADD KEY `idx_password_histories_created_at` (`created_at`);

ALTER TABLE `patients`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_patients_num_documento` (`num_documento`),
  ADD KEY `idx_patients_deleted_at` (`deleted_at`),
  ADD KEY `idx_patients_nombres_apellidos` (`nombres_apellidos`),
  ADD KEY `idx_patients_terapeuta_id` (`terapeuta_id`),
  ADD KEY `idx_patients_cuidador_id` (`responsable_id`),
  ADD KEY `idx_patients_created_at` (`created_at`),
  ADD KEY `idx_patients_serial_id` (`serial_id`);

ALTER TABLE `photo_changes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_photo_changes_user_id` (`user_id`),
  ADD KEY `idx_photo_changes_changed_at` (`changed_at`);

ALTER TABLE `profile_changes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_profile_changes_user_id` (`user_id`),
  ADD KEY `idx_profile_changes_changed_at` (`changed_at`);

ALTER TABLE `reportes`
  ADD PRIMARY KEY (`idreporte`),
  ADD KEY `idhijo` (`idpaciente`);

ALTER TABLE `reportes_analisis_emocional`
  ADD PRIMARY KEY (`idreporte_analisis`),
  ADD KEY `idx_paciente` (`idpaciente`),
  ADD KEY `idx_generado_por` (`generado_por_usuario_id`),
  ADD KEY `idx_tipo_reporte` (`tipo_reporte`),
  ADD KEY `idx_fecha_desde` (`fecha_desde`),
  ADD KEY `idx_fecha_hasta` (`fecha_hasta`),
  ADD KEY `idx_estado_generacion` (`estado_generacion`),
  ADD KEY `idx_fecha_creacion` (`fecha_creacion`);

ALTER TABLE `report_history`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_report_history_report_type` (`report_type`),
  ADD KEY `idx_report_history_session_id` (`session_id`),
  ADD KEY `idx_report_history_previous_therapist_id` (`previous_therapist_id`),
  ADD KEY `idx_report_history_created_at` (`created_at`),
  ADD KEY `idx_report_history_paciente_id` (`paciente_id`),
  ADD KEY `idx_report_history_generated_by_user_id` (`generated_by_user_id`);

ALTER TABLE `resumen_estadisticas_emociones`
  ADD PRIMARY KEY (`idresumen_estadisticas`),
  ADD KEY `idx_paciente` (`idpaciente`),
  ADD KEY `idx_anio` (`anio`),
  ADD KEY `idx_mes` (`mes`),
  ADD KEY `idx_fecha_creacion` (`fecha_creacion`),
  ADD KEY `idx_fecha_actualizacion` (`fecha_actualizacion`);

ALTER TABLE `roles`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_roles_name` (`name`);

ALTER TABLE `security_logs`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_security_logs_event_type` (`event_type`),
  ADD KEY `idx_security_logs_username` (`username`),
  ADD KEY `idx_security_logs_ip_address` (`ip_address`),
  ADD KEY `idx_security_logs_created_at` (`created_at`);

ALTER TABLE `therapist_disqualifications`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_therapist_disqualifications_therapist_id` (`therapist_id`),
  ADD KEY `idx_therapist_disqualifications_created_at` (`created_at`);

ALTER TABLE `therapist_ratings`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_therapist_ratings_session_id` (`session_id`),
  ADD KEY `idx_therapist_ratings_therapist_id` (`therapist_id`),
  ADD KEY `idx_therapist_ratings_caregiver_id` (`caregiver_id`),
  ADD KEY `idx_therapist_ratings_patient_id` (`patient_id`),
  ADD KEY `idx_therapist_ratings_created_at` (`created_at`);

ALTER TABLE `therapy_sessions`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_therapy_sessions_paciente_id` (`paciente_id`),
  ADD KEY `idx_therapy_sessions_terapeuta_id` (`terapeuta_id`),
  ADD KEY `idx_therapy_sessions_fecha_sesion` (`fecha_sesion`),
  ADD KEY `idx_therapy_sessions_estado` (`estado`),
  ADD KEY `idx_therapy_sessions_is_deleted` (`is_deleted`),
  ADD KEY `idx_therapy_sessions_created_at` (`created_at`),
  ADD KEY `idx_therapy_sessions_terapeuta_reasignado_id` (`terapeuta_reasignado_id`);

ALTER TABLE `user_device_ips`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_user_device_ips_user_id` (`user_id`);

ALTER TABLE `user_relationships`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_parent_child` (`parent_id`,`child_id`);

ALTER TABLE `user_roles`
  ADD PRIMARY KEY (`usuarios_id_usuario`,`roles_id`),
  ADD KEY `idx_user_roles_created_at` (`created_at`);

ALTER TABLE `usuarios`
  ADD PRIMARY KEY (`idusuario`),
  ADD UNIQUE KEY `idx_usuarios_num_documento` (`num_documento`),
  ADD UNIQUE KEY `idx_usuarios_correo` (`correo`),
  ADD UNIQUE KEY `idx_usuarios_usuario` (`usuario`),
  ADD KEY `idx_usuarios_activo` (`activo`),
  ADD KEY `idx_usuarios_created_at` (`created_at`),
  ADD KEY `idx_usuarios_activation_token` (`activation_token`),
  ADD KEY `idx_usuarios_activation_expiry` (`activation_expiry`),
  ADD KEY `idx_usuarios_telefono` (`telefono`),
  ADD KEY `idx_usuarios_last_login_at` (`last_login_at`),
  ADD KEY `idx_usuarios_password_expires_at` (`password_expires_at`),
  ADD KEY `idx_usuarios_tipo_documento` (`tipo_documento`),
  ADD KEY `idx_usuarios_nombres_apellidos` (`nombres_apellidos`);


ALTER TABLE `banner_changes`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `biometric_credentials`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `blogs`
  MODIFY `idblog` int NOT NULL AUTO_INCREMENT;

ALTER TABLE `emociones`
  MODIFY `idemocion` int NOT NULL AUTO_INCREMENT;

ALTER TABLE `emociones_detectadas`
  MODIFY `idemocion_detectada` int NOT NULL AUTO_INCREMENT;

ALTER TABLE `enlaces_sesion_emocion`
  MODIFY `idenlace_sesion_emocion` int NOT NULL AUTO_INCREMENT;

ALTER TABLE `login_histories`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `mediciones`
  MODIFY `idmedicion` int NOT NULL AUTO_INCREMENT;

ALTER TABLE `otps`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `otp_resends`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `password_histories`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `patients`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `photo_changes`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `profile_changes`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `reportes`
  MODIFY `idreporte` int NOT NULL AUTO_INCREMENT;

ALTER TABLE `reportes_analisis_emocional`
  MODIFY `idreporte_analisis` int NOT NULL AUTO_INCREMENT;

ALTER TABLE `report_history`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `resumen_estadisticas_emociones`
  MODIFY `idresumen_estadisticas` int NOT NULL AUTO_INCREMENT;

ALTER TABLE `roles`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `security_logs`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `therapist_disqualifications`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `therapist_ratings`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `therapy_sessions`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `user_device_ips`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `user_relationships`
  MODIFY `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `usuarios`
  MODIFY `idusuario` int NOT NULL AUTO_INCREMENT;


ALTER TABLE `blogs`
  ADD CONSTRAINT `blogs_ibfk_1` FOREIGN KEY (`idusuario`) REFERENCES `usuarios` (`idusuario`);

ALTER TABLE `emociones_detectadas`
  ADD CONSTRAINT `emociones_detectadas_ibfk_2` FOREIGN KEY (`idemocion`) REFERENCES `emociones` (`idemocion`);

ALTER TABLE `mediciones`
  ADD CONSTRAINT `mediciones_ibfk_1` FOREIGN KEY (`idreporte`) REFERENCES `reportes` (`idreporte`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
