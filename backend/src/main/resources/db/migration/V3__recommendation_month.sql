-- An old recommendation cannot cross a monthly performance/qualification boundary.
-- Legacy rows have no reliable calculation month and are treated as invalid.
ALTER TABLE recommendations ADD COLUMN calculation_month date
 CHECK(calculation_month IS NULL OR extract(day from calculation_month)=1);
