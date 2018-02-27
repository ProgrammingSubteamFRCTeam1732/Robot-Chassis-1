package org.usfirst.frc.team1732.robot.commands.recording;

import org.usfirst.frc.team1732.robot.Robot;
import org.usfirst.frc.team1732.robot.sensors.encoders.EncoderReader;
import org.usfirst.frc.team1732.robot.util.Pair;
import org.usfirst.frc.team1732.robot.util.SRXMomentRecorderM.Moment;

import com.ctre.phoenix.motorcontrol.ControlMode;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.Command;

public class ReverseWithVelocityM extends Command {
	
	private static final double HEADING_P = 1;
	
	private double last;
	private double total;
	private int i;
	
	private double initialHeading;
	
	public ReverseWithVelocityM() {
	}
	
	@Override
	protected void initialize() {
		initialHeading = Robot.sensors.navX.getTotalAngle();
	}
	
	@Override
	protected void execute() {
		Pair<Moment> now = Robot.recorderM.getNext(Timer.getFPGATimestamp() - last);
		total += (Timer.getFPGATimestamp() - last);
		i++;
		last = Timer.getFPGATimestamp();
		if (now != null) {
			double desiredHeading = (now.one.heading) - initialHeading;
			double currentHeading = Robot.sensors.navX.getTotalAngle();
			double headingError = desiredHeading - currentHeading;
			double headingAdjustment = -headingError * HEADING_P;
			Robot.drivetrain.leftTalon1.set(ControlMode.Velocity, Robot.drivetrain
					.convertVelocitySetpoint(-now.one.velocity + headingAdjustment * Math.signum(-now.one.velocity)));
			Robot.drivetrain.rightTalon1.set(ControlMode.Velocity, Robot.drivetrain
					.convertVelocitySetpoint(-now.two.velocity - headingAdjustment * Math.signum(-now.two.velocity)));
		}
	}
	
	@Override
	protected boolean isFinished() {
		return Robot.recorderM.isFinished();
	}
	
}