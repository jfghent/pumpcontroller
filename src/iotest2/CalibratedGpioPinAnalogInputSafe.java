/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iotest2;

import com.pi4j.io.gpio.GpioPinAnalogInput;
import static java.lang.Math.abs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jon
 */


public class CalibratedGpioPinAnalogInputSafe extends CalibratedGpioPinAnalogInput {
    
    SafeAdcInputMeasurementFailure failureCallback; //what to do if the rate of change exceeds the maximum
    double maxRateOfChange; //measured in units per second
    
    long lastMeasurementTime = -1;
    double lastMeasurement = -1.0;
    boolean firstMeasurementTaken = false;
    
    Logger logger = LoggerFactory.getLogger(IoTest2.class);
    
    public CalibratedGpioPinAnalogInputSafe(GpioPinAnalogInput ain, double adcVoltageRange, double maxAdcRegisterValue, SafeAdcInputMeasurementFailure failureCallback, double maxRateOfChange) {
        super(ain, adcVoltageRange, maxAdcRegisterValue);
        this.failureCallback = failureCallback;
        this.maxRateOfChange = maxRateOfChange;
    }

    public CalibratedGpioPinAnalogInputSafe(GpioPinAnalogInput ain, double adcVoltageRange, double maxAdcRegisterValue, AdcLinearCalibration cal, SafeAdcInputMeasurementFailure failureCallback, double maxRateOfChange) {
        super(ain, adcVoltageRange, maxAdcRegisterValue, cal);
        this.failureCallback = failureCallback;
        this.maxRateOfChange = maxRateOfChange;
    }
    
    @Override
    public double getValue() {
        return getAndCheckValue();
    }
    
    private double getAndCheckValue() {
        double value = gpioAnalogInputPin.getValue(); //read the ADC register raw value
        double voltage = adcVoltageRange * value / maxAdcRegisterValue; //calculate the voltage equivalent for the raw value
        double calibrated = cal.applyCal(voltage);
        long nowTime = System.currentTimeMillis();

        if(firstMeasurementTaken)
        {
            
            double rateOfChange = abs((calibrated-lastMeasurement)/((nowTime-lastMeasurementTime)/1000));
            //logger.info("****rateOfChange = " + rateOfChange);
            
            if(Double.isFinite(rateOfChange) && rateOfChange>maxRateOfChange)
            //if(abs((calibrated-lastMeasurement)/((nowTime-lastMeasurementTime)/1000))>maxRateOfChange)
            {
                //logger.info(" ++++ SHUTDOWN WOULD OCCUR HERE ++++");
                failureCallback.run(calibrated-lastMeasurement,(nowTime-lastMeasurementTime)/1000);
            }
        }
        
        lastMeasurementTime = nowTime;
        lastMeasurement = calibrated;
        firstMeasurementTaken = true;
        
        return calibrated; //apply the calibration
        
    }

   
}
