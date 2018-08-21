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
    public double x1 = 0;
    public double x2 = 0;
    public double y1 = 0;
    public double y2 = 0;
    public double b = 0;
    public double m = 1;
    public String uom = "units"; //unit of measure
    
    public AdcLinearCalibration(){
        
    }

    public AdcLinearCalibration(double x1, double x2, double y1, double y2, double b, String uom){
        this.x1 = x1; //lowest voltage input to the ADC
        this.x2 = x2; //highest voltage input to the ADC
        this.y1 = y1; //lowest reading of the sensor
        this.y2 = y2; //highest reading of the sensor
        this.b = b;   //linear offset (where the line intersects the y access)
        this.uom = uom;
        
        try{
            m = (y2-y1) / (x2-x1);
        } catch (Exception e) {
            //caught a divide-by-zero exception? //TODO: Something meaningful here
            m = 1;
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
