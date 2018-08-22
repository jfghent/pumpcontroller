/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iotest2;

import com.pi4j.io.gpio.GpioPinAnalogInput;

/**
 *
 * @author Jon
 */

//Can't extend GpioController, so this is a hack of a wrapper to include a crude calibration method.
public class CalibratedGpioPinAnalogInput {
    
    public GpioPinAnalogInput gpioAnalogInputPin;
    public AdcLinearCalibration cal;
    public double adcVoltageRange;
    public double maxAdcRegisterValue;
    
    public CalibratedGpioPinAnalogInput(GpioPinAnalogInput ain, double adcVoltageRange, double maxAdcRegisterValue ) {
        this.gpioAnalogInputPin = ain;
        this.adcVoltageRange = adcVoltageRange;
        this.maxAdcRegisterValue = maxAdcRegisterValue;
    }
    
    public CalibratedGpioPinAnalogInput(GpioPinAnalogInput ain, double adcVoltageRange, double maxAdcRegisterValue, AdcLinearCalibration cal) {
        this.gpioAnalogInputPin = ain;
        this.adcVoltageRange = adcVoltageRange;
        this.maxAdcRegisterValue = maxAdcRegisterValue;
        this.cal = cal;    
    }
    
    public double getValue() {
        double value = gpioAnalogInputPin.getValue(); //read the ADC register raw value
        //double percent =  ((value * 100) / maxAdcRegisterValue); //figure out where that is in the range
        //double voltage = adcVoltageRange * (percent/100);
        double voltage = adcVoltageRange * value / maxAdcRegisterValue; //calculate the voltage equivalent for the raw value
        return cal.applyCal(voltage); //apply the calibration
    }
    
}
