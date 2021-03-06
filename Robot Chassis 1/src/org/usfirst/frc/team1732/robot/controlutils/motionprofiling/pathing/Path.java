package org.usfirst.frc.team1732.robot.controlutils.motionprofiling.pathing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.usfirst.frc.team1732.robot.Util;
import org.usfirst.frc.team1732.robot.controlutils.Feedforward;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.math.BezierCurve;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.math.Curve;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.math.LineSegment;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.math.Vector;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.motion.GenerateMotionProfile;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.motion.MotionProfile;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.motion.MotionProfileConstraints;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.motion.MotionProfileGoal;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.motion.MotionProfileGoal.CompletionBehavior;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.motion.MotionSegment;
import org.usfirst.frc.team1732.robot.controlutils.motionprofiling.motion.MotionState;

import com.ctre.phoenix.motion.TrajectoryPoint;
import com.ctre.phoenix.motion.TrajectoryPoint.TrajectoryDuration;

/**
 * Class for making continuous-curvature paths.
 * 
 * Most basic shape combinations are continuous-curvature, I haven't tested it
 * all. There are some waypoint combinations where the paths that get made would
 * be very weird, so check all paths by graphing them
 * 
 * @author Jay
 *
 */
public final class Path {

	public final boolean driveForwards;
	private final ArrayList<PathSegment> segments = new ArrayList<>();
	private Waypoint prev;
	private MotionProfile profile;

	/**
	 * 
	 * @param first
	 *            first Waypoint
	 * @param driveForwards
	 *            if this path has the robot drive forwards or backwards
	 */
	public Path(Waypoint first, boolean driveForwards) {
		this.driveForwards = driveForwards;
		this.prev = first;
	}

	public void addWaypoints(Waypoint... waypoints) {
		for (Waypoint w : waypoints) {
			addWaypoint(w);
		}
	}

	public void addWaypoint(Waypoint w) {
		addWaypoint(w, 0.5);
	}

	public void addWaypoint(Waypoint w, double midControlPercent) {
		// System.out.println("ADDING WAYPOINT: " + w.toString());
		double theta1 = prev.heading.getAbsoluteAngle();
		double theta2 = w.heading.getAbsoluteAngle();

		Vector direction = w.position.subtract(prev.position);
		double alpha = direction.getAbsoluteAngle() - theta1;
		double beta = theta2 - theta1;

		// System.out.println("T1 " + theta1);
		// System.out.println("T2 " + theta2);
		// System.out.println("A " + alpha + ", " + Math.PI / 4);
		// System.out.println("B " + beta);
		Curve curve = null;
		if (Util.epsilonEquals(alpha, 0) && Util.epsilonEquals(beta, 0)) {
			// waypoints are colinear
			// System.out.println("Running thing 0");
			curve = new LineSegment(prev.position, w.position);
		} else if ((alpha >= 0 && beta < alpha) || (alpha <= 0 && beta > alpha)) {
			// not the same as checking if abs(beta) < abs(alpha)
			// System.out.println("Running thing 1");
			curve = addCase1(w, midControlPercent);
		} else if (Math.abs(beta) > Math.abs(alpha) && Math.abs(beta) <= 2 * Math.PI / 3) {
			// System.out.println("Running thing 2");
			curve = addCase2(w, midControlPercent);
		} else if (Math.abs(beta) > Math.abs(alpha) && Math.abs(beta) > 2 * Math.PI / 3) {
			// System.out.println("Running thing 3");
			curve = addCase3(w, midControlPercent);
		} else {
			System.err.println("INVALID WAYPOINT/BAD PATH");
		}
		if (curve != null) {
			segments.add(new PathSegment(prev, w, curve));
			prev = w;
		}
	}

	private BezierCurve addCase1(Waypoint w, double n) {
		// could have middle point be along direction at n, or be parallel at n
		// right now doing middle point along direction at n
		Vector v1 = prev.position;
		Vector v7 = w.position;
		LineSegment l17 = new LineSegment(v1, v7);
		Vector v4 = l17.getPointAtPercent(n);
		Vector h3 = prev.heading.rotate(-Math.PI / 2);
		Vector v3 = new LineSegment(v4, v4.add(h3)).getIntersection(new LineSegment(prev));
		Vector h5 = w.heading.rotate(Math.PI / 2);
		Vector v5 = new LineSegment(v4, v4.add(h5)).getIntersection(new LineSegment(w));
		Vector v2 = new LineSegment(v1, v3).getPointAtPercent(n);
		Vector v6 = new LineSegment(v5, v7).getPointAtPercent(1 - n);
		return new BezierCurve(v1, v2, v3, v4, v5, v6, v7);
	}

	private BezierCurve addCase2(Waypoint w, double n) {
		LineSegment lineA = new LineSegment(prev);
		LineSegment lineB = new LineSegment(w);
		// System.out.println("Line A: " + lineA.toString());
		// System.out.println("Line B: " + lineB.toString());
		Vector intersection = lineA.getIntersection(lineB);
		// System.out.println(intersection);
		Vector v1 = prev.position;
		Vector v5 = w.position;
		Vector v3 = intersection;
		LineSegment ac = new LineSegment(v1, v3);
		LineSegment ce = new LineSegment(v3, v5);
		Vector v2 = ac.getPointAtPercent(n);
		Vector v4 = ce.getPointAtPercent(1 - n);
		return new BezierCurve(v1, v2, v3, v4, v5);
	}

	private BezierCurve addCase3(Waypoint w, double n) {
		Vector v1 = prev.position;
		Vector v7 = w.position;
		LineSegment l17 = new LineSegment(v1, v7);
		Vector m4 = l17.getPointAtPercent(n);
		Vector a4 = l17.getDirection().rotate(Math.PI / 2);
		Vector v4 = m4.add(a4.scale(0.5));
		LineSegment l4 = new LineSegment(v4, v4.add(l17.getDirection()));
		Vector v3 = l4.getIntersection(new LineSegment(prev));
		Vector v5 = l4.getIntersection(new LineSegment(w));
		Vector v2 = new LineSegment(v1, v3).getPointAtPercent(n);
		Vector v6 = new LineSegment(v5, v7).getPointAtPercent(1 - n);
		return new BezierCurve(v1, v2, v3, v4, v5, v6, v7);
	}

	public void addPathSegment(PathSegment seg) {
		segments.add(seg);
		prev = seg.end;
	}

	public void addPathSegments(PathSegment... seg) {
		segments.addAll(Arrays.asList(seg));
		prev = seg[seg.length - 1].end;
	}

	public void generateProfile(double maxVelocity, double maxAcceleration) {
		for (PathSegment seg : segments) {
			if (seg.curve instanceof BezierCurve) {
				((BezierCurve) seg.curve).makeTable();
			}
		}
		MotionProfileConstraints constraints = new MotionProfileConstraints(maxVelocity, maxAcceleration);

		MotionState previousState = new MotionState(0, 0, 0, maxAcceleration);

		// System.out.println("Total length: " +
		// segments.get(0).curve.getTotalArcLength());
		double goalPos = segments.get(0).curve.getTotalArcLength() + previousState.pos();

		MotionProfileGoal goalState = new MotionProfileGoal(goalPos, Math.abs(segments.get(0).end.vel),
				CompletionBehavior.VIOLATE_MAX_ACCEL); // necessary because we restrict profiles to only moving forwards
														// or backwards
		MotionProfile currentProfile = GenerateMotionProfile.generateStraightMotionProfile(constraints, goalState,
				previousState);
		previousState = currentProfile.endState();

		for (int i = 1; i < segments.size(); i++) {
			goalPos = segments.get(i).curve.getTotalArcLength() + previousState.pos();
			goalState = new MotionProfileGoal(goalPos, segments.get(i).end.vel, CompletionBehavior.VIOLATE_MAX_ACCEL);
			currentProfile.appendProfile(
					GenerateMotionProfile.generateStraightMotionProfile(constraints, goalState, previousState));
			previousState = currentProfile.endState();
		}
		profile = currentProfile;
		if (!driveForwards) {
			for (MotionSegment s : profile.segments()) {
				s.setStart(s.start().flipped());
				s.setEnd(s.end().flipped());
			}
		}
	}

	public PathSegment[] getSegments() {
		return segments.toArray(new PathSegment[segments.size()]);
	}

	public MotionProfile getProfile() {
		return profile;
	}

	public TrajectoryHolder getTrajectoryPoints(double robotWidth, double pointDurationSec) {
		int cs = 0;
		PathSegment currentSegment = segments.get(0);
		double segmentLengthSum = 0;

		double duration = profile.duration();
		int pointCount = (int) (duration / pointDurationSec);
		double increment = duration / (pointCount - 1);

		MyPoint[] left = new MyPoint[pointCount];
		MyPoint[] right = new MyPoint[pointCount];

		MotionState previousState = profile.stateByTime(0).get();
		Vector coord = currentSegment.curve.getPointAtArcLength(0);

		left[0] = new MyPoint(coord.x, coord.y, previousState.pos(), previousState.vel(), previousState.acc(),
				pointDurationSec);
		right[0] = new MyPoint(coord.x, coord.y, previousState.pos(), previousState.vel(), previousState.acc(),
				pointDurationSec);

		for (int i = 1; i < pointCount; i++) {
			MotionState state = profile.stateByTimeClamped(i * increment);
			double currentLength = currentSegment.curve.getTotalArcLength();
			if (state.pos() > currentLength + segmentLengthSum) {
				if (cs + 1 >= segments.size()) {

				} else {
					cs++;
					segmentLengthSum += currentLength;
					currentSegment = segments.get(cs);
				}
			}
			/*
			 * Because K positive is curving to the left, and K negative is to the right,
			 * the side new radii =
			 * 
			 * Math.abs(1/K-a)
			 * 
			 * for left side, and
			 * 
			 * Math.abs(1/K+a)
			 * 
			 * for right side
			 */

			double curvature = currentSegment.curve.getCurvatureAtArcLength(state.pos() - segmentLengthSum);
			double dArc = state.pos() - previousState.pos();
			coord = currentSegment.curve.getPointAtArcLength(state.pos() - segmentLengthSum);
			// System.out.println(coord);
			if (Math.abs(curvature) < 1.0E-20) {
				left[i] = new MyPoint(coord.x, coord.y, left[i - 1].position + dArc, state.vel(), state.acc(),
						pointDurationSec);
				right[i] = new MyPoint(coord.x, coord.y, right[i - 1].position + dArc, state.vel(), state.acc(),
						pointDurationSec);
			} else {
				double r = 1 / curvature;
				double lR = Math.abs(r - robotWidth / 2);
				double rR = Math.abs(r + robotWidth / 2);
				r = Math.max(lR, rR);
				double lK = lR / r;
				double rK = rR / r;
				double leftV = state.vel() * lK;
				double rightV = state.vel() * rK;
				double leftA = (leftV - left[i - 1].velocity) / pointDurationSec;
				double rightA = (rightV - right[i - 1].velocity) / pointDurationSec;
				left[i] = new MyPoint(coord.x, coord.y, left[i - 1].position + dArc * lK, leftV, leftA,
						pointDurationSec);
				right[i] = new MyPoint(coord.x, coord.y, right[i - 1].position + dArc * rK, rightV, rightA,
						pointDurationSec);
			}
			previousState = state;
		}
		return new TrajectoryHolder(left, right);
	}

	public static boolean isPossible(TrajectoryHolder points, double lKv, double lKa, double lKs, double rKv,
			double rKa, double rKs) {
		double leftMaxVolt = 0;
		double rightMaxVolt = 0;
		for (int i = 0; i < points.left.length; i++) {
			leftMaxVolt = Math.max(leftMaxVolt,
					Math.abs(points.left[i].velocity * lKv + points.left[i].acceleration * lKa + lKs));
			rightMaxVolt = Math.max(rightMaxVolt,
					Math.abs(points.right[i].velocity * rKv + points.right[i].acceleration * rKa + rKs));
		}
		return leftMaxVolt <= 12 && rightMaxVolt <= 12;
	}

	public static class TrajectoryHolder {
		public final MyPoint[] left;
		public final MyPoint[] right;

		public TrajectoryHolder(MyPoint[] left, MyPoint[] right) {
			super();
			this.left = left;
			this.right = right;
		}
	}

	/**
	 * 
	 * File format: <br>
	 * t, dt, x, y, curvature, leftPos, leftVel, leftAcc, rightPos, rightVel,
	 * rightAcc <br>
	 * 0, 0.01, 0, 0, 0, ... (more numbers in each column) <br>
	 * 
	 * @param path
	 *            written file location
	 */
	public void writeToFile(String path) {

	}

	public static class PointPair<T> {
		public final T left;
		public final T right;

		public PointPair(T left, T right) {
			this.left = left;
			this.right = right;
		}

	}

	public static class MyIterator<T> implements Iterator<T> {

		private final Iterator<T> iterator;
		public final int baseDurationMs;
		public final double baseDurationSec;

		public MyIterator(Iterator<T> iterator, int baseDurationMs) {
			this.iterator = iterator;
			this.baseDurationMs = baseDurationMs;
			this.baseDurationSec = baseDurationMs / 1000.0;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public T next() {
			return iterator.next();
		}

	}

	private static double getLeftAdjust(Curve currentCurve, double robotWidth, double arcLength) {
		double curvature = currentCurve.getCurvatureAtArcLength(arcLength);
		double r = 1 / curvature;
		double lR = Math.abs(r - robotWidth / 2);
		double rR = Math.abs(r + robotWidth / 2);
		r = Math.max(lR, rR);
		return lR / r;
	}

	private static double getRightAdjust(Curve currentCurve, double robotWidth, double arcLength) {
		double curvature = currentCurve.getCurvatureAtArcLength(arcLength);
		double r = 1 / curvature;
		double lR = Math.abs(r - robotWidth / 2);
		double rR = Math.abs(r + robotWidth / 2);
		r = Math.max(lR, rR);
		return rR / r;
	}

	public static <T> MyIterator<PointPair<T>> getPreloadedIterator(MyIterator<PointPair<T>> input) {
		ArrayList<PointPair<T>> array = new ArrayList<>();
		while (input.hasNext()) {
			array.add(input.next());
		}
		return new MyIterator<PointPair<T>>(array.iterator(), input.baseDurationMs);
	}

	public MyIterator<PointPair<TrajectoryPoint>> getIteratorWithOffset(int baseDurationMs, Feedforward leftFF,
			Feedforward rightFF, int initialLeftSensorUnits, int initialRightSensorUnits, double robotWidth,
			double sensorUnitsPerYourUnits) {
		return getIterator(baseDurationMs, leftFF, rightFF, initialLeftSensorUnits, initialRightSensorUnits, robotWidth,
				sensorUnitsPerYourUnits, false);
	}

	public MyIterator<PointPair<TrajectoryPoint>> getIteratorZeroAtStart(int baseDurationMs, Feedforward leftFF,
			Feedforward rightFF, double robotWidth, double sensorUnitsPerYourUnits) {
		return getIterator(baseDurationMs, leftFF, rightFF, 0, 0, robotWidth, sensorUnitsPerYourUnits, true);
	}

	public MyIterator<PointPair<TrajectoryPoint>> getIterator(int baseDurationMs, Feedforward leftFF,
			Feedforward rightFF, int initialLeftSensorUnits, int initialRightSensorUnits, double robotWidth,
			double sensorUnitsPerYourUnits, boolean zeroAtStart) {
		Iterator<PointPair<TrajectoryPoint>> iterator = new Iterator<PointPair<TrajectoryPoint>>() {

			int cs = 0;
			PathSegment currentSegment = segments.get(0);
			double currentSegmentLength = currentSegment.curve.getTotalArcLength();
			double segmentLengthSum = 0;

			double totalTime = profile.duration();
			double pointDurationSec = baseDurationMs / 1000.0;
			int pointCount = (int) (totalTime / (pointDurationSec));
			double timeIncrement = totalTime / (pointCount - 1);
			MotionState currentStartState = profile.stateByTimeClamped(0);

			TrajectoryDuration additionalDuration = TrajectoryDuration.Trajectory_Duration_0ms;

			double prevLeftEndPos = 0;
			double prevRightEndPos = 0;
			double prevLeftEndVel = 0;
			double prevRightEndVel = 0;

			int i = 0;

			@Override
			public boolean hasNext() {
				return i < pointCount;
			}

			@Override
			public PointPair<TrajectoryPoint> next() {
				TrajectoryPoint leftPoint = new TrajectoryPoint();
				TrajectoryPoint rightPoint = new TrajectoryPoint();
				leftPoint.timeDur = additionalDuration;
				rightPoint.timeDur = additionalDuration;
				leftPoint.headingDeg = 0;
				rightPoint.headingDeg = 0;
				leftPoint.isLastPoint = false;
				rightPoint.isLastPoint = false;
				leftPoint.profileSlotSelect0 = 0;
				rightPoint.profileSlotSelect1 = 0;
				leftPoint.zeroPos = false;
				rightPoint.zeroPos = false;

				MotionState currentEndState = profile.stateByTimeClamped((i) * timeIncrement);
				// System.out.println("End Pos: " + currentEndState.pos());
				if (Math.abs(currentEndState.pos()) >= currentSegmentLength + segmentLengthSum) {
					if (cs < segments.size() - 1) {
						System.out.println("currentState pos is greater than current curve length");
						segmentLengthSum += currentSegmentLength;
						cs++;
						currentSegment = segments.get(cs);
						currentSegmentLength = currentSegment.curve.getTotalArcLength();
					}
				}

				double endCurvature = currentSegment.curve
						.getCurvatureAtArcLength(Math.abs(currentEndState.pos()) - segmentLengthSum);
				double dCenterArc = currentEndState.pos() - currentStartState.pos();

				// double leftEndVel;
				// double rightEndVel;

				double leftStartAcc;
				double rightStartAcc;

				if (Math.abs(endCurvature) < 1.0E-35) { // if straight
					leftPoint.position = (prevLeftEndPos + dCenterArc) * sensorUnitsPerYourUnits
							+ initialLeftSensorUnits;
					rightPoint.position = (prevRightEndPos + dCenterArc) * sensorUnitsPerYourUnits
							+ initialRightSensorUnits;
					prevLeftEndPos = prevLeftEndPos + dCenterArc;
					prevRightEndPos = prevRightEndPos + dCenterArc;
					// leftEndVel = currentEndState.vel();
					// rightEndVel = currentEndState.vel();
					// leftStartAcc = (leftEndVel - prevLeftEndVel) / pointDurationSec;
					// rightStartAcc = (rightEndVel - prevRightEndVel) / pointDurationSec;

					leftStartAcc = leftFF.getInitialAcceleration(dCenterArc, pointDurationSec, prevLeftEndVel,
							driveForwards);
					rightStartAcc = rightFF.getInitialAcceleration(dCenterArc, pointDurationSec, prevRightEndVel,
							driveForwards);
				} else { // if curving
					double startArcLength = Math.abs(currentStartState.pos()) - segmentLengthSum;
					double endArcLength = Math.abs(currentEndState.pos()) - segmentLengthSum;

					double dLeftArc = Math.signum(currentEndState.pos()) * Util.gaussQuadIntegrate64(
							(d) -> getLeftAdjust(currentSegment.curve, robotWidth, d), startArcLength, endArcLength);
					double dRightArc = Math.signum(currentEndState.pos()) * Util.gaussQuadIntegrate64(
							(d) -> getRightAdjust(currentSegment.curve, robotWidth, d), startArcLength, endArcLength);
					leftPoint.position = (prevLeftEndPos + dLeftArc) * sensorUnitsPerYourUnits + initialLeftSensorUnits;
					rightPoint.position = (prevRightEndPos + dRightArc) * sensorUnitsPerYourUnits
							+ initialRightSensorUnits;
					prevLeftEndPos = prevLeftEndPos + dLeftArc;
					prevRightEndPos = prevRightEndPos + dRightArc;

					// leftEndVel = currentEndState.vel() * getLeftAdjust(currentSegment.curve,
					// robotWidth, endArcLength);
					// rightEndVel = currentEndState.vel()
					// * getRightAdjust(currentSegment.curve, robotWidth, endArcLength);
					// leftStartAcc = (leftEndVel - prevLeftEndVel) / pointDurationSec;
					// rightStartAcc = (rightEndVel - prevRightEndVel) / pointDurationSec;

					leftStartAcc = leftFF.getInitialAcceleration(dLeftArc, pointDurationSec, prevLeftEndVel,
							driveForwards);
					rightStartAcc = rightFF.getInitialAcceleration(dRightArc, pointDurationSec, prevRightEndVel,
							driveForwards);
				}
				leftPoint.velocity = 1023 / 12.0 * leftFF.getAppliedVoltage(prevLeftEndVel, leftStartAcc);
				rightPoint.velocity = 1023 / 12.0 * rightFF.getAppliedVoltage(prevRightEndVel, rightStartAcc);

				// System.out.println("Left: " + prevLeftEndPos + ", " + prevLeftEndVel + ", " +
				// leftStartAcc + ", "
				// + leftPoint.velocity);
				// System.out.println("Right: " + prevRightEndPos + ", " + prevRightEndVel + ",
				// " + rightStartAcc + ", "
				// + rightPoint.velocity);

				// prevLeftEndVel = leftEndVel;
				// prevRightEndVel = rightEndVel;

				prevLeftEndVel = leftFF.getVelocityAtTime(prevLeftEndVel, leftStartAcc, pointDurationSec,
						driveForwards);
				prevRightEndVel = rightFF.getVelocityAtTime(prevRightEndVel, rightStartAcc, pointDurationSec,
						driveForwards);

				if (i == 0) {
					leftPoint.zeroPos = zeroAtStart;
					rightPoint.zeroPos = zeroAtStart;
				}
				if (i == pointCount - 1) {
					leftPoint.isLastPoint = true;
					rightPoint.isLastPoint = true;
				}
				currentStartState = currentEndState;
				i++;
				return new PointPair<TrajectoryPoint>(leftPoint, rightPoint);
			}
		};
		return new MyIterator<PointPair<TrajectoryPoint>>(iterator, baseDurationMs);
	}

	public static class VelocityPoint {
		public double velocity;
		public double headingDeg;

		public VelocityPoint() {
			velocity = 0;
			headingDeg = 0;
		}

		public VelocityPoint(double vel, double head) {
			this.velocity = vel;
			this.headingDeg = head;
		}
	}

	public static class PointProfile {

		public final double pointDurSec;
		public final int pointDurMs;
		public double totalTimeSec;
		public final double finalAbsCenterPos;
		public final double initialHeading;

		private final TreeMap<Double, PointPair<VelocityPoint>> map;

		public PointProfile(MyIterator<PointPair<VelocityPoint>> iterator, double finalAbsCenterPos,
				double initialHeading) {
			totalTimeSec = 0;
			this.pointDurSec = iterator.baseDurationSec;
			this.pointDurMs = iterator.baseDurationMs;
			this.finalAbsCenterPos = finalAbsCenterPos;
			this.initialHeading = initialHeading;
			map = new TreeMap<>();
			while (iterator.hasNext()) {
				PointPair<VelocityPoint> pair = iterator.next();
				map.put(totalTimeSec, pair);
				// System.out.println(pair.left.velocity + ", " + pair.right.velocity);
				totalTimeSec += pointDurSec;
			}
		}

		public PointProfile(Iterator<PointPair<VelocityPoint>> iterator, double finalAbsCenterPos,
				double initialHeading) {
			totalTimeSec = 0;
			// this.pointDurSec = iterator.baseDurationSec;
			this.pointDurSec = 0;
			this.pointDurMs = 0;
			// this.pointDurMs = iterator.baseDurationMs;
			this.finalAbsCenterPos = finalAbsCenterPos;
			this.initialHeading = initialHeading;
			map = new TreeMap<>();
			while (iterator.hasNext()) {
				PointPair<VelocityPoint> pair = iterator.next();
				map.put(totalTimeSec, pair);
				// System.out.println(pair.left.velocity + ", " + pair.right.velocity);
				totalTimeSec += pointDurSec;
			}
		}

		public double getTotalTimeSec() {
			return totalTimeSec;
		}

		public PointPair<VelocityPoint> getInterpolatedPoint(double timeSec) {
			PointPair<VelocityPoint> value = map.get(timeSec);
			if (value != null) {
				return value;
			}
			Entry<Double, PointPair<VelocityPoint>> lower = map.floorEntry(timeSec);
			Entry<Double, PointPair<VelocityPoint>> upper = map.ceilingEntry(timeSec);

			if (lower == null && upper == null) {
				System.err.println("ERROR: BOTH SHOULDN'T BE NULL");
				return null;
			} else if (lower == null) {
				return upper.getValue();
			} else if (upper == null) {
				return lower.getValue();
			}

			VelocityPoint leftLow = lower.getValue().left;
			VelocityPoint leftUp = upper.getValue().left;

			VelocityPoint rightLow = lower.getValue().right;
			VelocityPoint rightUp = upper.getValue().right;

			double dt = upper.getKey() - lower.getKey();
			double mu = (timeSec - lower.getKey()) / dt;
			double leftV = Util.lerp(leftLow.velocity, leftUp.velocity, mu);
			double rightV = Util.lerp(rightLow.velocity, rightUp.velocity, mu);
			double leftH = Util.lerp(leftLow.headingDeg, leftUp.headingDeg, mu);
			double rightH = Util.lerp(rightLow.headingDeg, rightUp.headingDeg, mu);
			return new PointPair<VelocityPoint>(new VelocityPoint(leftV, leftH), new VelocityPoint(rightV, rightH));
		}

		public PointPair<VelocityPoint> getCeilingPoint(double timeSec) {
			PointPair<VelocityPoint> value = map.get(timeSec);
			if (value != null) {
				return value;
			}
			Entry<Double, PointPair<VelocityPoint>> lower = map.floorEntry(timeSec);
			Entry<Double, PointPair<VelocityPoint>> upper = map.ceilingEntry(timeSec);

			if (lower == null && upper == null) {
				System.err.println("ERROR: BOTH SHOULDN'T BE NULL");
				return null;
			} else if (lower == null) {
				return upper.getValue();
			} else if (upper == null) {
				return lower.getValue();
			}

			VelocityPoint upperLeft = upper.getValue().left;
			VelocityPoint upperRight = upper.getValue().right;
			return new PointPair<VelocityPoint>(new VelocityPoint(upperLeft.velocity, upperLeft.headingDeg),
					new VelocityPoint(upperRight.velocity, upperRight.headingDeg));
		}
	}

	public PointProfile getVelocityProfile(double robotWidth) {
		return new PointProfile(getVelocityIterator(robotWidth), Math.abs(getProfile().endPos()),
				Math.toDegrees(getOriginalHeading()));
	}

	public MyIterator<PointPair<VelocityPoint>> getVelocityIterator(double robotWidth) {
		int pointDurationMs = 20; // navx can only update at 200 Hz
		Iterator<PointPair<VelocityPoint>> iterator = new Iterator<PointPair<VelocityPoint>>() {

			int cs = 0;
			PathSegment currentSegment = segments.get(0);
			double currentSegmentLength = currentSegment.curve.getTotalArcLength();
			double segmentLengthSum = 0;

			double totalTime = profile.duration();
			double pointDurationSec = pointDurationMs / 1000.0;
			int pointCount = (int) (totalTime / (pointDurationSec)) + 1; // because we start at i=0 instead of 1

			int i = 0;

			@Override
			public boolean hasNext() {
				return i < pointCount;
			}

			@Override
			public PointPair<VelocityPoint> next() {
				VelocityPoint leftPoint = new VelocityPoint();
				VelocityPoint rightPoint = new VelocityPoint();

				MotionState currentEndState = profile.stateByTimeClamped((i) * pointDurationSec);
				// System.out.println("End Pos: " + currentEndState.pos());
				if (Math.abs(currentEndState.pos()) >= currentSegmentLength + segmentLengthSum) {
					System.out.println("CurrentState pos is greater than current curve length");
					if (cs < segments.size() - 1) {
						System.out.println("Selecting the next curve.");
						segmentLengthSum += currentSegmentLength;
						cs++;
						currentSegment = segments.get(cs);
						currentSegmentLength = currentSegment.curve.getTotalArcLength();
					} else {
						System.out.println("No more curves available.");
					}
				}

				double endCurvature = currentSegment.curve
						.getCurvatureAtArcLength(Math.abs(currentEndState.pos()) - segmentLengthSum);
				double endArcLength = Math.abs(currentEndState.pos()) - segmentLengthSum;
				double endVel = profile.velocityByTimeClamped((i) * pointDurationSec);
				double endHeading = Math.toDegrees(currentSegment.curve.getHeadingAtArcLength(endArcLength));

				leftPoint.headingDeg = endHeading;
				rightPoint.headingDeg = endHeading;

				if (Util.epsilonEquals(0, endCurvature, 1.0e-30)) { // if straight
					leftPoint.velocity = endVel;
					rightPoint.velocity = endVel;
				} else { // if curving
					double curvature = currentSegment.curve.getCurvatureAtArcLength(endArcLength);
					double r = 1 / curvature;
					double lR = Math.abs(r - robotWidth / 2);
					double rR = Math.abs(r + robotWidth / 2);
					r = Math.abs(r);
					double lK = lR / r;
					double rK = rR / r;
					leftPoint.velocity = endVel * lK;
					rightPoint.velocity = endVel * rK;
				}
				i++;
				return new PointPair<VelocityPoint>(leftPoint, rightPoint);
			}
		};
		return new MyIterator<PointPair<VelocityPoint>>(iterator, pointDurationMs);
	}

	public double getOriginalHeading() {
		return segments.get(0).start.heading.getHeading();
	}

}