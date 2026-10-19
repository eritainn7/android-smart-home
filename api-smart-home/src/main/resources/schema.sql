CREATE TABLE IF NOT EXISTS device_groups (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(owner_id, name)
);
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES device_groups(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    homie_device_id VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS device_properties (
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    property_name VARCHAR(64) NOT NULL,
    property_value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(device_id, property_name)
);
CREATE INDEX IF NOT EXISTS idx_device_groups_owner ON device_groups(owner_id);
CREATE INDEX IF NOT EXISTS idx_devices_group ON devices(group_id);
