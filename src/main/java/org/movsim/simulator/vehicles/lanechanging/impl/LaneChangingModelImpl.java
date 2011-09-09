/**
 * Copyright (C) 2010, 2011 by Arne Kesting, Martin Treiber,
 *                             Ralph Germ, Martin Budden
 *                             <info@movsim.org>
 * ----------------------------------------------------------------------
 * 
 *  This file is part of 
 *  
 *  MovSim - the multi-model open-source vehicular-traffic simulator 
 *
 *  MovSim is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  MovSim is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with MovSim.  If not, see <http://www.gnu.org/licenses/> or
 *  <http://www.movsim.org>.
 *  
 * ----------------------------------------------------------------------
 */
package org.movsim.simulator.vehicles.lanechanging.impl;

import java.util.List;

import org.movsim.input.model.vehicle.laneChanging.LaneChangingInputData;
import org.movsim.input.model.vehicle.laneChanging.LaneChangingMobilData;
import org.movsim.input.model.vehicle.longModel.AccelerationModelInputDataIDM;
import org.movsim.simulator.Constants;
import org.movsim.simulator.roadSection.impl.OnrampImpl;
import org.movsim.simulator.vehicles.Moveable;
import org.movsim.simulator.vehicles.Vehicle;
import org.movsim.simulator.vehicles.VehicleContainer;
import org.movsim.simulator.vehicles.impl.VehicleImpl;
import org.movsim.simulator.vehicles.lanechanging.LaneChangingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class LaneChangingModelImpl.
 */
public class LaneChangingModelImpl implements LaneChangingModel {
    
    /** The Constant logger. */
    final static Logger logger = LoggerFactory.getLogger(LaneChangingModelImpl.class);

    
    private static final int MOST_RIGHT = Constants.MOST_RIGHT_LANE;

    private static final int TO_LEFT = 1;

    private static final int TO_RIGHT = -1;

    private static final int NO_CHANGE = 0;
    
    public static boolean WITH_INSTANT_LANECHANGE = true;
    
    
    public static double P_FACTOR_CAR = 0.1;// 0.25;//0.4 // courtesy/politeness
    // lane change model for onramp vehicles (more aggressive)
    // cars and trucks with same lc-model!
    // OnRamp Modeling:
    public static double RMP_BSAFE_MAX = 7.0; // !!!!!!!! (how to provoke TB?)
    // high to force merging, increase to max value
    public static double RMP_BIAS_FACTOR = 2.0;
    public static double RMP_SECTION_OFFSET_M = 150; // upstream start of changed
    // on Mainroad
    public static double ALPHA_T_RMP = 0.7;
    public static double ALPHA_A_RMP = 1.5;
    public static double OFFRMP_BSAFE_MAX = 10; // TODO
    public static boolean OFFRAMP_WITH_LT = true;
    // to avoid flips:
    public static double LANECHANGE_TDELAY_S = 3.0; // delay nach Spurwechsel
    public static double LANECHANGE_TDELAY_FRONT_S = 3.0; // delay nach
    // gleiche Zeitskala fuer relaxation der reduzierten Folgezeit nach erfolgtem
    // Spurwechsel!!!
    public static double LANE_INVERSION_ALPHA = 0.1; // reduce distance s for
    // arne fuer offramp MOBIL!!!!
    public static double ALPHA_T_AFTER_LANECHANGE = 1.0; // 0.5 decrease of T
    public static boolean WITH_LT_COUPLING = false; // global on/off switch
    // applies for veh on ramp and for mandatory lc in lane closing scenario!
    public static double LT_AMAX_ACTIVE = 3.0; // 2.0 // 0... max. transversal
    // applies as reaction to vehicles on onramp and on lane-closing lane
    public static double LT_AMAX_PASSIVE = 2.0;// 1.5; //1.0//!!!!
    // arne 23-6-04: "normal" LT as coupling to righthandside
    // together with anisotropy of 1 (only braking)!
    public static boolean WITH_LT_COUPLING_FREEROAD = false; // switch for
    public static double LT_AMAX_FREEROAD = 0.3;
    
    public static double ACTUAL_LANE_WIDTH = 3;
    
    public static double A_LANECHANGE = 2.0; // transversal acceleration (m/s^2)
    
    private boolean withEuropeanRules = false;

    // crit. velocity where Europ rules kick in (in m/s):
    private double vCritEur;

    private double p; // politeness factor

    private double threshold; // changing threshold

    private double bSafe; // maximum safe braking decel

    private double gapMin; // minimum safe (net) distance

    private double biasRight; // bias (m/s^2) to drive

    // internal: save reference values
    private double thresholdRef;

    private double biasRightRef;

    private double bSafeRef;

    private double pRef;

    // arne, 26.2.2004
    // mandatory lane change (lane closing, on-ramp)
    // possible values: NO_CHANGE, TO_LEFT, TO_RIGHT

    private int mandatoryChange = NO_CHANGE; // init

    //double alpha_s = LANE_INVERSION_ALPHA;

    double lane = 0; // fractional values during lane changes

    int targetLane = 0;

    int startLane = 0;
    
    protected double tdelay = 0;
    
    double vel_trans = 0;
    double acc_trans = 0;


    final LaneChangingMobilData mobilParameter;


    private VehicleImpl me;
    
    private final boolean isInitialized;
    
    
    public LaneChangingModelImpl(LaneChangingInputData lcInputData){
	//logger.debug("init model parameters");
        this.withEuropeanRules = lcInputData.isWithEuropeanRules();
        this.vCritEur = lcInputData.getCritSpeedEuroRules();
        
        
        isInitialized = lcInputData.isInitializedMobilData();
        
        // valid lane change model only if configured by xml 
        mobilParameter = lcInputData.getLcMobilData();
        if(isInitialized){
        bSafeRef = bSafe = mobilParameter.getSafeDeceleration();
        biasRightRef = biasRight = mobilParameter.getRightBiasAcceleration();
        gapMin = mobilParameter.getMinimumGap();
        thresholdRef = threshold = mobilParameter.getThresholdAcceleration();
        pRef = p = mobilParameter.getPoliteness();
        }
    }

    public final int targetLane() {
        return (targetLane);
    }

    public final int startLane() {
        return startLane;
    }

//    public boolean laneChanging() {
//        return (laneChangeStatus() != NO_CHANGE);
//    }
//
//    public final int laneChangeStatus() {
//        double dir = targetLane - startLane;
//        if (dir > 0)
//            return (TO_RIGHT);
//        else
//            if (dir < 0) return (TO_LEFT);
//        return (NO_CHANGE);
//    }
    
        
    
    
    @Override
    public boolean checkLaneChangeFromRamp(double dt, final VehicleContainer vehContainerTargetLane){
//        if (laneChangeStatus() == NO_CHANGE) {
            final boolean otherVehsChangeSufficientlyLongAgo = true; 
            final Vehicle frontMain = vehContainerTargetLane.getLeader(me); // works also for the "virtual" leader of me in considered lane
            final Vehicle backMain = vehContainerTargetLane.getFollower(me); 
            
            final boolean changeSafe = mandatoryWeavingChange(frontMain, backMain); // TODO
//            if (otherVehsChangeSufficientlyLongAgo && changeSafe) {
//                lane = startLane = MOST_RIGHT + TO_RIGHT; // count
//                targetLane = MOST_RIGHT;
//                resetDelay();
////                System.out.println("updateLaneChangeStatusOnRamp: safety OK:"
////                      + " Starting lanechange ..." + " laneChangeStatus=" + laneChangeStatus()
////                      + " targetLane=" + targetLane + " startLane=" + startLane);
//            }
//        } 
//        else {
//            testLaneChangeFinish();
//        }
            return changeSafe;
    }
    
    // Flips unterbinden durch Karenzzeit LANECHANGE_TDELAY_S nach erfolgtem Spurwechsel
    private void resetDelay() {
        tdelay = 0;
    }
    
    protected boolean delayOK() {
        return (tdelay >= LANECHANGE_TDELAY_S);
    }

    // auch karenzzeit bezueglich Wecsel des Vorderfahrzeugs noetig ...
    public boolean delayFrontVehOK() {
        return (tdelay >= LANECHANGE_TDELAY_FRONT_S);
    }

    protected void updateDelay(double dt) {
        tdelay += dt;
    }

    
    
 // test if actual lane change is over
    // during changing, transversal velocity same sign as
    // (targetLane-startLane)
    protected void testLaneChangeFinish() {
        boolean targetLaneReached = (Math.abs(targetLane - lane) < 0.00001);
        boolean movingToTargetLane = (vel_trans * (targetLane - startLane) >= 0);
        if (targetLaneReached || (!movingToTargetLane)) {
            // if(!movingToTargetLane){
            // Logger.log("Vehicle: finishing lanechange !!");
            // laneChangeStatus=NO_CHANGE;
            resetDelay();
            startLane = targetLane;
            lane = targetLane; // eliminates erors from integrating trans. mov.
            vel_trans = 0;
            acc_trans = 0;
            //Logger.log("reset Mandatory LC");
            setMandatoryChange(NO_CHANGE);
        }
    }
    
    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    // Lane Change from OnRamp to Mainroad
    // and from Mainroad to OffRamp
    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    
    private boolean mandatoryWeavingChange(final Vehicle frontVeh, final Vehicle backVeh) {

        // safety incentive (in two steps)
        final double gapFront = me.getNetDistance(frontVeh);
        final double gapBack  = (backVeh == null) ? Constants.GAP_INFINITY : backVeh.getNetDistance(me);

        // (i) first check distances
        // negative netto distances possible because of different veh lengths!
        if ((gapFront < gapMin) || (gapBack < gapMin)) {
            logger.debug("gapFront={}, gapBack={}", gapFront, gapBack);
            return false; 
        }

        final double backNewAcc = (backVeh == null) ? 0 : backVeh.getAccelerationModel().calcAcc(backVeh, me);

        // (ii) check security constraint for new follower
        // normal acceleration generally admitted here
        if (backNewAcc <= -bSafe) {
            logger.debug("gapFront = {}, gapBack = {}", gapFront, gapBack);
            logger.debug("backNewAcc={}, bSafe={}", backNewAcc, bSafe);
            return (false);
        }
        
        final double meNewAcc = me.getAccelerationModel().calcAcc(me, frontVeh);
        if (meNewAcc >= -bSafeRef) {
            logger.debug("meNewAcc={}, bSafe={}", meNewAcc, bSafe);
            logger.debug("gapFront={}, gapBack={}", gapFront, gapBack);
            logger.debug("backNewAcc={}, bSafe={}", backNewAcc, bSafe);
            return (true);
        }
        return (false);
    }

 
    public void initialize(VehicleImpl vehicleImpl) {
	this.me = vehicleImpl;
    }

    
    
    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    // Basic MOBIL strategy:
    // asymmetric rules for european traffic
    // symmetric rules for US traffic
    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    public boolean considerLaneChanging(final List<VehicleContainer> vehContainers) {

	final int currentLane = me.getLane();
	
	// init with largest possible deceleration 
        double accToLeft = -Double.MAX_VALUE;
        double accToRight = -Double.MAX_VALUE;

	
	// consider lane-changing to right (decreasing lane index)
	if( currentLane-1 >= Constants.MOST_RIGHT_LANE ){
	    accToRight = calcAccelerationInNewLane(vehContainers.get(currentLane), vehContainers.get(currentLane-1) );
	}
	
	// consider lane-changing to left (increasing the lane index)
	if( currentLane+1 < vehContainers.size() ){
	    accToLeft = calcAccelerationInNewLane(vehContainers.get(currentLane), vehContainers.get(currentLane+1));
	}
	
        // decision process: set new target lane 
        if ((accToRight > 0) || (accToLeft > 0)) {
            logger.debug("accToRight={}, accToLeft={}", accToRight, accToLeft);
            logger.debug("currentLane={}", currentLane);
            if (accToRight > accToLeft) {
        	me.setTargetLane(currentLane+Constants.TO_RIGHT);
        	
            } else {
        	me.setTargetLane(currentLane+Constants.TO_LEFT);
            }
            return true;   
        }
        
        return false;
    }

    private double calcAccelerationInNewLane(final VehicleContainer ownLane, final VehicleContainer newLane) {
	return calcAccelerationBalanceInNewLaneSymmetric(ownLane, newLane);
    }
    
    

    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    // Symmetric MOBIL criterion
    // calc balance:
    // deltaMe+p*(deltaOldBack+deltaNewBack)-threshold-bias
    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    private double calcAccelerationBalanceInNewLaneSymmetric(final VehicleContainer ownLane, final VehicleContainer newLane) {

        // apply only in case of mandatory lane change!!!
        // double alpha_T = (mandatoryChange==NO_CHANGE)? 1 :
        // ALPHA_T_AFTER_LANECHANGE;

        double prospectiveBalance = -Double.MAX_VALUE;

        // (1) check "karenzzeit" of vehicles ahead
        final Vehicle newFront = newLane.getLeader(me);
        
        final Vehicle oldFront = ownLane.getLeader(me); 

//        if (!lastChangeSufficientlyLongAgo(newFront, oldFront))
//            return (prospectiveBalance);

        // (2) safety incentive (in two steps)

        final Vehicle newBack = newLane.getFollower(me);

        double gapFront = me.getNetDistance(newFront);
        double gapBack = (newBack == null) ? Constants.GAP_INFINITY : newBack.getNetDistance(me);

        // (i) first check distances
        // negative net distances possible because of different veh lengths!
        if ((gapFront < gapMin) || (gapBack < gapMin)) {
            return prospectiveBalance;
        }

        final double newBackNewAcc = (newBack == null) ? 0 : newBack.getAccelerationModel().calcAcc(newBack, me); 
            
        // (ii) check (MOBIL) security constraint for new follower

        if ( newBackNewAcc <= -bSafe ) {
            return prospectiveBalance;
        }

        // (3)check now incentive criterion
         
        
        final double meNewAcc = me.getAccelerationModel().calcAcc(me, newFront);
        final double meOldAcc = me.getAccelerationModel().calcAcc(me, oldFront);

        // check for mandatory change
//        if (mandatoryLaneChange(changeTo, meNewAcc, me, newBack, microstreet.mostRightLane()))
//            return (ModelConstants.BMAX);

        // calculate accelerations of new and old back vehicle
        // for actual and prospective situation
        // newBackNewAcc already calculated above
        // int iOldBack = me.iBack();
        // IMoveableExt oldBack = (iOldBack == -1) ? null : microstreet.vehContainer().get(iOldBack);
        final Vehicle oldBack = ownLane.getFollower(me);

        final double oldBackOldAcc = (oldBack != null) ? oldBack.getAccelerationModel().calcAcc(oldBack, me) : 0;
        final double oldBackNewAcc = (oldBack != null) ? oldBack.getAccelerationModel().calcAcc(oldBack, newFront) : 0;
        final double newBackOldAcc = (newBack != null) ? newBack.getAccelerationModel().calcAcc(newBack, newFront) : 0;

        // (ii) MOBIL trade-off for driver and neigbourhood
        // (gleichmaessiges Auffuellen der Spur:)
        // vehicles already on the correct lane want to change
        // if other lane is empty
        final double oldBackDiffAcc = oldBackNewAcc - oldBackOldAcc;
        final double newBackDiffAcc = newBackNewAcc - newBackOldAcc;
        final double meDiffAcc = meNewAcc - meOldAcc;

        // bias sign applies for euro as symmetric rules!!!
        // but in case of mandatory lc the sign is modified!
        final int changeTo = newLane.getLaneIndex() - ownLane.getLaneIndex();
        final double biasSign = (changeTo == Constants.TO_LEFT) ? 1 : -1;

        final double actualBiasRight = biasRight;
        // if(mandatoryChange!=NO_CHANGE){actualBiasRight = mandatoryBias;}
       
        // finally: all in one MOBIL's incentive formula:
        prospectiveBalance = meDiffAcc + p * (oldBackDiffAcc + newBackDiffAcc) - threshold - biasSign * actualBiasRight;
       
        return prospectiveBalance;
    }

    
    
    
    // perform actual lane change
       // updates acc_trans, vel_trans, and (double-valued) lane=transv. position
       // could also be called accelerate_trans(dt) + translate_trans(dt)

       public void translate_trans(double dt) {
           if (WITH_INSTANT_LANECHANGE) {
               lane = targetLane;
               // laneChangeStatus=NO_CHANGE;
               startLane = targetLane;
               testLaneChangeFinish(); // arne 20.11.2007 testweise um mandatory wieder zu "reseten"
           } else {
               if (Math.abs(targetLane - lane) < 0.00001) {
//                 Logger.log("Vehicle.translate_trans: "
//                         + " targetLane=lane: omitting update of" + " acc_trans ...");
//                 printDiagnostics();
               } else {
                   boolean firstPhase = (Math.abs(targetLane - lane) > 0.5 * Math.abs(targetLane
                           - startLane));
                   // acc_trans crucial for CoffeeMeter dynamics:
                   acc_trans = (firstPhase) ? A_LANECHANGE * (targetLane - startLane)
                           : -0.5 * vel_trans * vel_trans / ACTUAL_LANE_WIDTH / (targetLane - lane);
                   vel_trans += acc_trans * dt;
               }

//             Logger.log("pos=" + (int) pos + " lane=" + lane + " targetLane="
//                     + targetLane + " vel_trans=" + vel_trans + " acc_trans=" + acc_trans);
               lane += (vel_trans * dt - 0.5 * acc_trans * dt * dt) / ACTUAL_LANE_WIDTH;

               testLaneChangeFinish(); // Treiber 28.09.04

               // MULTILANE:
               // if(!((lane>-1)&&(lane<MOST_RIGHT))){Logger.log("error:
               // lane="+lane);}
           } // of else (with continous lane change)
       } // of translate_trans
       
       
       
       public void setMandatoryChange(int incentive) {
           if (incentive == NO_CHANGE || incentive == TO_RIGHT || incentive == TO_LEFT) {
               mandatoryChange = incentive;
               System.out.println("LaneChange.setMandatoryChange:" + " mandatoryChange= " + mandatoryChange);
           } else {
//               Logger.log("LaneChange.setMandatoryChange:");
//               Logger.log("Value error: incentive = " + incentive);
               System.exit(-1); // debugging
           }
       }

    public boolean isInitialized() {
	return isInitialized;
    }
    
    
}
