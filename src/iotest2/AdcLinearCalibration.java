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
//Creates a simple linear calibration based on the y=mx+b formula for a line.
//Assumes x is the voltage range of the ADC and y is the matching range of the sensor/transducer.
//uom is for storing the unit of measure - for example, PSI or Amps
    
public class AdcLinearCalibration {
    // y = mx+b, m = rise/run = (y2-y1)/(x2-x1)
    //private double x1 = 0;
    //private double x2 = 0;
    //private double y1 = 0;
    //private double y2 = 0;
    private double b = 0;
    private double m = 1;
    private String uom = "units"; //unit of measure
    
    //public AdcLinearCalibration(){
    //    
    //}

    public AdcLinearCalibration(double x1, double x2, double y1, double y2, double b, String uom){
        //this.x1 = x1; //lowest voltage input to the ADC
        //this.x2 = x2; //highest voltage input to the ADC
        //this.y1 = y1; //lowest reading of the sensor
        //this.y2 = y2; //highest reading of the sensor
        this.b = b;   //linear offset (where the line intersects the y access)
        this.uom = uom;
        
        try{
            m = (y2-y1) / (x2-x1);
            
            if(Double.isInfinite(m)) throw new java.lang.IllegalArgumentException("Bad paramater passed while initializing AdcLinearCalibration resulting in infinite slope for calibration.  x1 = " + x1 + "  x2 = " + x2);
        } catch (java.lang.IllegalArgumentException iae){
            m = 1;
            throw iae;
        } catch (Exception e){
            m = -1;
            throw new IllegalArgumentException("Unhandled exception while constructing AdcLinearCalibration during calculation of slope (variable for slope is the double 'm'.");
        }
        
    }
    
    public String getUnits(){
        return this.uom;
    }
    
    public double applyCal(double value){
        double cal;
        //y = (m*x) + b
        cal = this.m * value + this.b;
        
        return cal;
    }

}
