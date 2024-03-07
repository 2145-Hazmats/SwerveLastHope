// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.CANSparkBase.ControlType;
import com.revrobotics.CANSparkLowLevel.MotorType;

import java.util.function.DoubleSupplier;

import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkPIDController;

import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.units.Angle;
import edu.wpi.first.units.Measure;
import edu.wpi.first.units.MutableMeasure;
import edu.wpi.first.units.Units;
import edu.wpi.first.units.Velocity;
import edu.wpi.first.units.Voltage;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.sysid.SysIdRoutineLog;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.robot.Constants.ArmConstants;
import frc.robot.Constants.ArmConstants.ArmState;


public class ArmSubsystem extends SubsystemBase {
  // Declare and intialize motors and encoders
  private final CANSparkMax elbowMotorLeader = new CANSparkMax(ArmConstants.kElbowMotorLeaderID, MotorType.kBrushless);
  private final CANSparkMax elbowMotorFollower = new CANSparkMax(ArmConstants.kElbowMotorFollowerID, MotorType.kBrushless);
  private RelativeEncoder elbowEncoder = elbowMotorLeader.getAlternateEncoder(8192);

  private final CANSparkMax wristMotor = new CANSparkMax(ArmConstants.kWristMotorID, MotorType.kBrushless);
  //private RelativeEncoder wristEncoder = wristMotor.getAlternateEncoder(8192);
  private final RelativeEncoder wristEncoder = wristMotor.getEncoder();
  // Get the PIDController object for the elbow and wrist
  private SparkPIDController elbowPIDController = elbowMotorLeader.getPIDController();
  private SparkPIDController wristPIDController = wristMotor.getPIDController();
  // Variables used during SmartDashboard changes
  private double elbowP, elbowI, elbowD, elbowFF, elbowSetPoint = 0;
  private double wristP, wristI, wristD, wristFF, wristSetPoint = 0;
  // Arm state
  // We make this static because we want to use it without an object of arm subsystem. We can use this in other subsystems even though its private, because we return it in a public get method
  private static ArmState currentPosition = ArmState.IDLE;

  /* SysID variables and routine */
  // Mutable holder for unit-safe voltage values, persisted to avoid reallocation
  private final MutableMeasure<Voltage> m_appliedVoltage = MutableMeasure.mutable(Units.Volts.of(0));
  // Mutable holder for unit-safe linear distance values, persisted to avoid reallocation
  private final MutableMeasure<Angle> m_angle = MutableMeasure.mutable(Units.Rotations.of(0));
  // Mutable holder for unit-safe linear velocitry values, persisted to avoid reallocation
  private final MutableMeasure<Velocity<Angle>> m_velocity = MutableMeasure.mutable(Units.RotationsPerSecond.of(0));

  /*private SysIdRoutine armSysIdRoutine = new SysIdRoutine(
    new SysIdRoutine.Config(),
    new SysIdRoutine.Mechanism(
        this::motorVoltageControl,
        this::logMotor,
        this)
  );*/


  /** Creates a new Arm. */
  public ArmSubsystem() {
    // Have the second elbow motor follow the first
    elbowMotorFollower.follow(elbowMotorLeader, true);
    // Enable voltage compensation
    elbowMotorLeader.enableVoltageCompensation(ArmConstants.kElbowMotorNominalVoltage);
    elbowMotorFollower.enableVoltageCompensation(ArmConstants.kElbowMotorNominalVoltage);
    wristMotor.enableVoltageCompensation(ArmConstants.kWristMotorNominalVoltage);

    // Setup encoders
    elbowEncoder.setPositionConversionFactor(360);
    wristEncoder.setPositionConversionFactor(1);
    elbowEncoder.setPosition(0);
    wristEncoder.setPosition(0);
    // Invert the elbow encoder. Mandatory
    elbowEncoder.setInverted(true);

    // Change PIDController FeedbackDevice from the integrated encoder to the alternate encoder
    elbowPIDController.setFeedbackDevice(elbowEncoder);
    wristPIDController.setFeedbackDevice(wristEncoder);

    // Setup the elbow PIDController
    elbowPIDController.setP(ArmConstants.kElbowP);
    elbowPIDController.setI(ArmConstants.kElbowI);
    elbowPIDController.setD(ArmConstants.kElbowD);
    elbowPIDController.setFF(ArmConstants.kElbowFF);
    elbowPIDController.setOutputRange(ArmConstants.kElbowMinSpeed, ArmConstants.kElbowMaxSpeed);

    // Setup the wrist PIDController
    wristPIDController.setP(ArmConstants.kWristP);
    wristPIDController.setI(ArmConstants.kWristI);
    wristPIDController.setD(ArmConstants.kWristD);
    wristPIDController.setFF(ArmConstants.kWristFF);
    wristPIDController.setOutputRange(ArmConstants.kWristMinSpeed, ArmConstants.kWristMaxSpeed);

    /* // Put Elbow PIDs on SmartDashboard  
    SmartDashboard.putNumber("Elbow P", ArmConstants.kElbowP);
    SmartDashboard.putNumber("Elbow I", ArmConstants.kElbowI);
    SmartDashboard.putNumber("Elbow D", ArmConstants.kElbowD);
    SmartDashboard.putNumber("Elbow FF", ArmConstants.kElbowFF);
    SmartDashboard.putNumber("Elbow Set Point", 0); 
    
    // Put Wrist PIDs on SmartDashboard
    SmartDashboard.putNumber("Wrist P", ArmConstants.kWristP);
    SmartDashboard.putNumber("Wrist I", ArmConstants.kWristI);
    SmartDashboard.putNumber("Wrist D", ArmConstants.kWristD);
    SmartDashboard.putNumber("Wrist FF", ArmConstants.kWristFF);
    SmartDashboard.putNumber("Wrist Set Point", 0);*/
  } 

  /**
   * Sets the reference angle of the elbow and wrist.
   * Until the PIDController is given another angle or ControlType, the PID will stay on.
   *
   * @param elbowAngle  The angle the elbow will rotate to and stay at.
   * @param wristAngle  The angle the wrist will rotate to and stay at.
   */
  public Command setArmPIDCommand(ArmState position, boolean stayAtSetpoint) {
    return startEnd(
      // When the command is called, the elbow and wrist PIDController is set and updated on SmartDashboard
      () -> {
        double elbowAngle = 0;
        double wristAngle = 0;
        // Updates currentPosition static var
        currentPosition = position;
        // it's like a fancy switching thingy where it like checks if your in like a position and then it goes to that position there and does whatever you tell it to and then when you finish the thingy you tell it to, the "break;" makes it got to the end of the big list and then continues on with its life. 
        // - Carson 2024
        switch(position) {
          case IDLE:
            elbowAngle = ArmConstants.kIdleAngleSP[0];
            wristAngle = ArmConstants.kIdleAngleSP[1];
            break;
          case SOURCE:
            elbowAngle = ArmConstants.kSourceAngleSP[0];
            wristAngle = ArmConstants.kSourceAngleSP[1];
            break;
          case FLOOR:
            elbowAngle = ArmConstants.kFloorAngleSP[0];
            wristAngle = ArmConstants.kFloorAngleSP[1];
            break;
          case AMP:
            elbowAngle = ArmConstants.kAmpAngleSP[0];
            wristAngle = ArmConstants.kAmpAngleSP[1];
            break;
          case SHOOT_SUB:
            elbowAngle = ArmConstants.kSpeakerSubwooferAngleSP[0];
            wristAngle = ArmConstants.kSpeakerSubwooferAngleSP[1];
            break;
          case TRAP:
            elbowAngle = ArmConstants.kTrapAngleSP[0];
            wristAngle = ArmConstants.kTrapAngleSP[1];
            break;
          case CLIMBING_POSITION:
            elbowAngle = ArmConstants.kClimbingAngleSP[0];
            wristAngle = ArmConstants.kClimbingAngleSP[1];
            break;
          case SHOOT_HORIZONTAL:
            elbowAngle = ArmConstants.kHorizontalAngleSP[0];
            wristAngle = ArmConstants.kHorizontalAngleSP[1];
            break;
          case SHOOT_PODIUM:
            elbowAngle = ArmConstants.kSpeakerPodiumAngleSP[0];
            wristAngle = ArmConstants.kSpeakerPodiumAngleSP[1];
            break;
          default:
            break;
        }

        elbowPIDController.setReference(elbowAngle, ControlType.kPosition);
        wristPIDController.setReference(wristAngle, ControlType.kPosition);
        SmartDashboard.putNumber("Elbow Set Point", elbowAngle);
        SmartDashboard.putNumber("Wrist Set Point", wristAngle);
      },
      // When the command is interrupted, the elbow and wrist go to their idle position if stayAtSetpoint is false
      () -> {
        if (!stayAtSetpoint) { 
          currentPosition = ArmConstants.ArmState.IDLE;
          elbowPIDController.setReference(ArmConstants.kIdleAngleSP[0], ControlType.kPosition);
          wristPIDController.setReference(ArmConstants.kIdleAngleSP[1], ControlType.kPosition);
        }
    
      }
    );
  }

  /**
   * Stops the arm by setting the PIDControllers to a value of 0 with a type of ControlType.kVelocity.
   */
  /*public Command stopArmCommand() {
    return runOnce(() -> {
      elbowPIDController.setReference(0, ControlType.kVelocity);
      wristPIDController.setReference(0, ControlType.kVelocity);
    });
  }*/
 
  public void resetWristEncoder() {
    wristEncoder.setPosition(0);
  };

  /*public double getWristEncoder() {
    return wristEncoder.getPosition();
  }

  public double getWristVelocity() {
    return wristEncoder.getVelocity();
  }*/
 
  /**
   * Sets the elbow motor speed.
   * 
   * @param speed  The speed of the elbow motor.
   */
  /*public void setElbowSpeed(double speed) {
    elbowMotorLeader.set(speed);
  }*/

  /**
   * Sets the elbow motor speed and wrist motor speed in manual mode by giving their PIDControllers
   * a speed in ControlType.kDutyCycle mode.
   * 
   * @param wristSpeed  The speed of the wrist motor from a joystick axis.
   * @param elbowSpeed  The speed of the elbow motor from a joystick axis.
   */
  public Command manualArmCommand(DoubleSupplier wristSpeed, DoubleSupplier elbowSpeed){
    return runOnce(() -> currentPosition = ArmState.MANUAL)
      .andThen(run(()->{
        wristPIDController.setReference(wristSpeed.getAsDouble(), ControlType.kDutyCycle);
        elbowPIDController.setReference(elbowSpeed.getAsDouble(), ControlType.kDutyCycle);
      }));
  }

  // We should not use this method guys its super bad - Cody ( this is a joke - Miles)
  public Command TurnPIDOff(){
    return runOnce(()->{
      wristPIDController.setReference(0, ControlType.kDutyCycle);
      elbowPIDController.setReference(0, ControlType.kDutyCycle);
    });
  }

  /*public Command sysIdQuasistaticCommand(SysIdRoutine.Direction direction) {
    return armSysIdRoutine.quasistatic(direction);
  }

  public Command sysIdDynamicCommand(SysIdRoutine.Direction direction) {
    return armSysIdRoutine.dynamic(direction);
  }

  public void motorVoltageControl(Measure<Voltage> volts) {
    elbowMotorLeader.setVoltage(volts.in(Units.Volts));
  }

  public void logMotor(SysIdRoutineLog log) {
    log.motor("elbow-motor")
      .voltage(m_appliedVoltage.mut_replace(elbowMotorLeader.get() * RobotController.getBatteryVoltage(), Units.Volts))
      .angularPosition(m_angle.mut_replace(elbowEncoder.getPosition(), Units.Rotations))
      .angularVelocity(m_velocity.mut_replace(elbowEncoder.getVelocity(), Units.RotationsPerSecond));
  }*/

  /**
   * Sets the wrist motor speed.
   * 
   * @param speed  The speed of the wrist motor.
   */
  /*public void setWristSpeed(double speed) {
    wristMotor.set(speed);
  }*/

  /**
   * returns our the state of our arm as an enum. 
   * 
   * @return
   */
  public static ArmState getArmState() {
    return currentPosition;
  }

  @Override
  public void periodic() {
    // If the elbow PID or setpoint values are different from SmartDashboard, use the new values
    /*if (elbowP != SmartDashboard.getNumber("Elbow P", 0)) {
      elbowP = SmartDashboard.getNumber("Elbow P", 0);
      elbowPIDController.setP(elbowP);
    }
    if (elbowI != SmartDashboard.getNumber("Elbow I", 0)) {
      elbowI = SmartDashboard.getNumber("Elbow I", 0);
      elbowPIDController.setI(elbowI);
    }
    if (elbowD != SmartDashboard.getNumber("Elbow D", 0)) {
      elbowD = SmartDashboard.getNumber("Elbow D", 0);
      elbowPIDController.setD(elbowD);
    }
    if (elbowFF != SmartDashboard.getNumber("Elbow FF", 0)) {
      elbowFF = SmartDashboard.getNumber("Elbow FF", 0);
      elbowPIDController.setFF(elbowFF);
    }
    if (elbowSetPoint != SmartDashboard.getNumber("Elbow Set Point", 0)) {
      elbowSetPoint = SmartDashboard.getNumber("Elbow Set Point", 0);
      elbowPIDController.setReference(elbowSetPoint, ControlType.kPosition);
    }

    // If the wrist PID or setpoint values are different from SmartDashboard, use the new values
    if (wristP != SmartDashboard.getNumber("Wrist P", 0)) {
      wristP = SmartDashboard.getNumber("Wrist P", 0);
      wristPIDController.setP(wristP);
    }
    if (wristI != SmartDashboard.getNumber("Wrist I", 0)) {
      wristI = SmartDashboard.getNumber("Wrist I", 0);
      wristPIDController.setI(wristI);
    }
    if (wristD != SmartDashboard.getNumber("Wrist D", 0)) {
      wristD = SmartDashboard.getNumber("Wrist D", 0);
      wristPIDController.setD(wristD);
    }
    if (wristFF != SmartDashboard.getNumber("Wrist FF", 0)) {
      wristFF = SmartDashboard.getNumber("Wrist FF", 0);
      wristPIDController.setFF(wristFF);
    }
    if (wristSetPoint != SmartDashboard.getNumber("Wrist Set Point", 0)) {
      wristSetPoint = SmartDashboard.getNumber("Wrist Set Point", 0);
      wristPIDController.setReference(wristSetPoint, ControlType.kPosition);
    }*/
  
    // Update SmartDashboard with elbow and wrist information
    SmartDashboard.putNumber("Elbow Angular Velocity", elbowEncoder.getVelocity());
    SmartDashboard.putNumber("Elbow Angle", elbowEncoder.getPosition());
    SmartDashboard.putNumber("Wrist Angular Velocity", wristEncoder.getVelocity());
    SmartDashboard.putNumber("Wrist Angle", wristEncoder.getPosition());
    SmartDashboard.putString("NameofEnum", getArmState().toString());
  }

}
