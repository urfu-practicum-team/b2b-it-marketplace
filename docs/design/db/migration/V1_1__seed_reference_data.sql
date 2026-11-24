-- V1.1 — справочные данные: навыки и стартовая ставка комиссии.
-- Админа создавайте через приложение (пароль хэшируется BCrypt).

INSERT INTO skills (name, category) VALUES
 ('Java', 'Backend'), ('Spring Boot', 'Backend'), ('Kotlin', 'Backend'),
 ('Python', 'Backend'), ('Node.js', 'Backend'), ('C#/.NET', 'Backend'), ('Go', 'Backend'),
 ('React', 'Frontend'), ('TypeScript', 'Frontend'), ('Vue.js', 'Frontend'), ('Angular', 'Frontend'),
 ('PostgreSQL', 'Data'), ('Redis', 'Data'), ('Kafka', 'Data'), ('SQL', 'Data'),
 ('Docker', 'DevOps'), ('Kubernetes', 'DevOps'), ('CI/CD', 'DevOps'), ('Linux', 'DevOps'),
 ('Manual QA', 'QA'), ('Test Automation', 'QA'),
 ('UI/UX Design', 'Design'), ('Figma', 'Design'),
 ('Android', 'Mobile'), ('iOS', 'Mobile'),
 ('Business Analysis', 'Analytics'), ('Project Management', 'Management'),
 ('Technical Writing', 'Documentation');

-- Ставка комиссии платформы, % (допустимо 10–15; меняется через админ-панель)
INSERT INTO platform_settings (key, value) VALUES ('commission_rate_percent', '12.00');
