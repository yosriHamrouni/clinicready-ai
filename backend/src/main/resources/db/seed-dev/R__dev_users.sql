-- Synthetic login users for local development (profile "dev" only).
-- Password for every user: "password". Never use in production.

INSERT INTO clinic (id, name, city)
VALUES ('0b6a4c1e-8d7f-4c2a-9e55-3a1d2f4b5c6d', 'Clinique Test Nabeul', 'Nabeul')
ON CONFLICT (id) DO NOTHING;

INSERT INTO practitioner (id, clinic_id, full_name, specialty)
VALUES ('9a2d7c3b-1e4f-4b6a-8c5d-2f3e4a5b6c70', '0b6a4c1e-8d7f-4c2a-9e55-3a1d2f4b5c6d',
        'Dr Karim Test', 'Radiologie')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
VALUES
    ('6f1c1c4e-3b0a-4a49-9a51-1f9f6a1d2b10', '0b6a4c1e-8d7f-4c2a-9e55-3a1d2f4b5c6d',
     'reception@clinic.test', '$2y$10$VZGczp9dfCRqdr.f2HOcEOXF9addK2mJc/shVOM/b57lpZJuxehoi',
     'Sarra Test', 'RECEPTIONIST'),
    ('6f1c1c4e-3b0a-4a49-9a51-1f9f6a1d2b11', '0b6a4c1e-8d7f-4c2a-9e55-3a1d2f4b5c6d',
     'doctor@clinic.test', '$2y$10$VZGczp9dfCRqdr.f2HOcEOXF9addK2mJc/shVOM/b57lpZJuxehoi',
     'Dr Karim Test', 'PRACTITIONER'),
    ('6f1c1c4e-3b0a-4a49-9a51-1f9f6a1d2b12', '0b6a4c1e-8d7f-4c2a-9e55-3a1d2f4b5c6d',
     'manager@clinic.test', '$2y$10$VZGczp9dfCRqdr.f2HOcEOXF9addK2mJc/shVOM/b57lpZJuxehoi',
     'Leila Test', 'CLINIC_MANAGER')
ON CONFLICT (id) DO NOTHING;

-- The doctor user is the practitioner above.
UPDATE app_user SET practitioner_id = '9a2d7c3b-1e4f-4b6a-8c5d-2f3e4a5b6c70'
WHERE id = '6f1c1c4e-3b0a-4a49-9a51-1f9f6a1d2b11';
