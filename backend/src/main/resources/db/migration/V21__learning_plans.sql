CREATE TABLE learning_plans (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  goal VARCHAR(1000) NOT NULL,
  focus_skill VARCHAR(20) NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  time_zone VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  INDEX ix_learning_plan_owner (user_id,status,id),
  CONSTRAINT fk_learning_plan_owner FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_learning_plan_status CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED')),
  CONSTRAINT ck_learning_plan_duration CHECK (DATEDIFF(end_date,start_date) IN (0,6)),
  CONSTRAINT ck_learning_plan_focus CHECK (focus_skill IN ('LISTENING','PRONUNCIATION','CONVERSATION','REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE learning_plan_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  due_date DATE NOT NULL,
  position INT NOT NULL,
  lesson_id BIGINT NULL,
  review_item_id BIGINT NULL,
  source_title_snapshot VARCHAR(240) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
  completed_at DATETIME(6) NULL,
  INDEX ix_learning_plan_items_order (plan_id,due_date,position,id),
  CONSTRAINT fk_learning_item_plan FOREIGN KEY (plan_id) REFERENCES learning_plans(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_item_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE SET NULL,
  CONSTRAINT fk_learning_item_card FOREIGN KEY (review_item_id) REFERENCES review_items(id) ON DELETE SET NULL,
  CONSTRAINT ck_learning_item_position CHECK (position>0),
  CONSTRAINT ck_learning_item_status CHECK (status IN ('PLANNED','COMPLETED','SKIPPED')
    AND ((status='COMPLETED')=(completed_at IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
