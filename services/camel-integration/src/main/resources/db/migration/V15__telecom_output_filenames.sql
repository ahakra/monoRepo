-- Upgrade only the original demo filenames; preserve customized connector settings.
-- Split the dollar sign from the expression so Flyway does not treat it as a placeholder.
UPDATE etl_connections SET updated_at = CURRENT_TIMESTAMP
WHERE id IN ('ce1e0000-0000-4000-8000-000000000011', 'ce1e0000-0000-4000-8000-000000000012', 'ce1e0000-0000-4000-8000-000000000013')
AND EXISTS (
    SELECT 1 FROM etl_connection_settings s WHERE s.connection_id = etl_connections.id
    AND s.setting_key = 'fileName'
    AND ((s.connection_id = 'ce1e0000-0000-4000-8000-000000000011' AND s.setting_value = 'cdr.csv')
      OR (s.connection_id = 'ce1e0000-0000-4000-8000-000000000012' AND s.setting_value = 'sms.csv')
      OR (s.connection_id = 'ce1e0000-0000-4000-8000-000000000013' AND s.setting_value = 'rejected.csv'))
);

UPDATE etl_connection_settings SET setting_value = '$' || '{file:onlyname.noext.single}-cdr.csv'
WHERE connection_id = 'ce1e0000-0000-4000-8000-000000000011' AND setting_key = 'fileName' AND setting_value = 'cdr.csv';
UPDATE etl_connection_settings SET setting_value = '$' || '{file:onlyname.noext.single}-sms.csv'
WHERE connection_id = 'ce1e0000-0000-4000-8000-000000000012' AND setting_key = 'fileName' AND setting_value = 'sms.csv';
UPDATE etl_connection_settings SET setting_value = '$' || '{file:onlyname.noext.single}-rejected.csv'
WHERE connection_id = 'ce1e0000-0000-4000-8000-000000000013' AND setting_key = 'fileName' AND setting_value = 'rejected.csv';
