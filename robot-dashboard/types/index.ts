export interface RobotLog {
  id: number;
  robotId: string;
  cpuUsage: number;
  memUsed: number;
  memTotal: number;
  procsRunning: number;
  procsBlocked: number;
  priority: number;
  posX: number;
  posY: number;
  posZ: number;
  velLinearX: number;
  velLinearY: number;
  velAngularZ: number;
  rosFrameId: string;
  rosTopic: string;
  recordedAt: string;
}
