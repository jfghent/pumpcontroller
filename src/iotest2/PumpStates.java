/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iotest2;

/**
 *
 * @author Jon
 */
public class PumpStates {
    public static enum State{
        NONE, INIT, FULLY_CHARGED, PARTIALLY_CHARGED, 
        PUMP_START_INIT, PUMP_START, PUMPING, 
        PUMP_REST,
        WELL_REST_START, WELL_REST
    }
}
