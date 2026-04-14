-- Initial sample data for robots
-- Adding logs for 5 robots so they appear in the dashboard list on startup

-- Robot 1
INSERT INTO robot_logs (robot_id, cpu_usage, mem_used, mem_total, procs_running, procs_blocked, priority, pos_x, pos_y, pos_z, vel_linear_x, vel_linear_y, vel_angular_z, ros_frame_id, ros_topic, recorded_at)
VALUES ('robot-1', 12.5, 1024, 8192, 10, 0, 1, 1.2, 3.4, 0.0, 0.5, 0.0, 0.1, 'base_link', '/robot1/odom', NOW());

-- Robot 2
INSERT INTO robot_logs (robot_id, cpu_usage, mem_used, mem_total, procs_running, procs_blocked, priority, pos_x, pos_y, pos_z, vel_linear_x, vel_linear_y, vel_angular_z, ros_frame_id, ros_topic, recorded_at)
VALUES ('robot-2', 45.0, 4096, 8192, 25, 2, 2, -5.0, 2.0, 0.5, 0.0, 0.0, 0.0, 'base_link', '/robot2/odom', NOW());

-- Robot 3
INSERT INTO robot_logs (robot_id, cpu_usage, mem_used, mem_total, procs_running, procs_blocked, priority, pos_x, pos_y, pos_z, vel_linear_x, vel_linear_y, vel_angular_z, ros_frame_id, ros_topic, recorded_at)
VALUES ('robot-3', 5.5, 512, 4096, 5, 0, 3, 0.0, 0.0, 0.0, 1.2, 0.0, -0.2, 'base_link', '/robot3/odom', NOW());

-- Robot 4
INSERT INTO robot_logs (robot_id, cpu_usage, mem_used, mem_total, procs_running, procs_blocked, priority, pos_x, pos_y, pos_z, vel_linear_x, vel_linear_y, vel_angular_z, ros_frame_id, ros_topic, recorded_at)
VALUES ('robot-4', 70.0, 6000, 8192, 30, 1, 2, 10.0, -10.0, 0.0, 0.0, 0.0, 0.5, 'base_link', '/robot4/odom', NOW());

-- Robot 5
INSERT INTO robot_logs (robot_id, cpu_usage, mem_used, mem_total, procs_running, procs_blocked, priority, pos_x, pos_y, pos_z, vel_linear_x, vel_linear_y, vel_angular_z, ros_frame_id, ros_topic, recorded_at)
VALUES ('robot-5', 20.0, 2048, 8192, 12, 0, 4, -2.5, -2.5, 0.0, -0.5, 0.5, 0.0, 'base_link', '/robot5/odom', NOW());
