-- Sample data for IMC Accident MCP Server

-- Insert sample customers
INSERT INTO customers (customer_id, first_name, last_name, email, phone_number, address, city, state, zip_code) VALUES
(100001, 'John', 'Doe', 'john.doe@email.com', '555-0101', '123 Main St', 'Atlanta', 'GA', '30309'),
(100002, 'Jane', 'Smith', 'jane.smith@email.com', '555-0102', '456 Oak Ave', 'Savannah', 'GA', '31401'),
(100003, 'Bob', 'Johnson', 'bob.johnson@email.com', '555-0103', '789 Pine Rd', 'Augusta', 'GA', '30901'),
(100004, 'Alice', 'Wilson', 'alice.wilson@email.com', '555-0104', '321 Elm St', 'Columbus', 'GA', '31901'),
(100005, 'Charlie', 'Brown', 'charlie.brown@email.com', '555-0105', '654 Maple Dr', 'Macon', 'GA', '31201');

-- Insert sample accidents
INSERT INTO accidents (accident_id, policy_id, vehicle_id, driver_id, accident_timestamp, latitude, longitude, g_force, description) VALUES
(1, 201, 301, 100001, '2024-03-15 14:30:00-05:00', 33.7490, -84.3880, 12.5, 'Rear-end collision at intersection during heavy traffic'),
(2, 202, 302, 100002, '2024-03-18 09:15:00-05:00', 32.0835, -81.0998, 8.2, 'Minor fender bender in parking lot'),
(3, 203, 303, 100001, '2024-04-02 16:45:00-05:00', 33.4734, -82.0105, 15.8, 'Side impact collision at four-way stop'),
(4, 204, 304, 100003, '2024-04-10 11:20:00-05:00', 32.4609, -84.9877, 22.1, 'High-speed collision on highway - emergency response required'),
(5, 205, 305, 100004, '2024-04-22 19:30:00-05:00', 32.8407, -83.6324, 6.9, 'Low-speed collision in residential area'),
(6, 206, 306, 100002, '2024-05-01 13:10:00-05:00', 32.0835, -81.0998, 11.3, 'Collision with stationary object - utility pole'),
(7, 207, 307, 100005, '2024-05-15 08:45:00-05:00', 32.8407, -83.6324, 18.7, 'Multi-vehicle accident during morning rush hour'),
(8, 208, 308, 100003, '2024-05-28 20:15:00-05:00', 33.4734, -82.0105, 14.2, 'Weather-related accident - wet road conditions'),
(9, 209, 309, 100001, '2024-06-05 12:00:00-05:00', 33.7490, -84.3880, 25.9, 'Severe impact collision - airbag deployment detected'),
(10, 210, 310, 100004, '2024-06-18 17:25:00-05:00', 32.4609, -84.9877, 9.4, 'Minor collision in shopping center parking lot');