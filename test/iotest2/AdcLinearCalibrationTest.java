/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iotest2;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 *
 * @author Jon
 */
public class AdcLinearCalibrationTest {
    
    public AdcLinearCalibrationTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }

    /**
     * Test of getUnits method, of class AdcLinearCalibration.
     */
    @Test
    public void testGetUnits() {
        System.out.println("getUnits");
        
        String expResult = "ABC123$#@___";
        
        AdcLinearCalibration instance = new AdcLinearCalibration(1.0,2.0,1.0,2.0,0.0,expResult);
        
        //#1: test for uom value
        assertEquals(expResult, instance.getUnits());
        
    }

    /**
     * Test of applyCal method, of class AdcLinearCalibration.
     */
    @Test
    public void testApplyCal() {
        System.out.println("applyCal");
        AdcLinearCalibration instance;
        double expResult;
        
        //args: double x1, double x2, double y1, double y2, double b, String uom
        
        //#1: 
        instance = new AdcLinearCalibration(1.0,2.0,1.0,2.0,0.0,"unit_of_meas");
        expResult = 1.0;
        assertEquals("Nominal case  ",expResult,instance.applyCal(1.0),0.0001);
        
        //#2
        instance = new AdcLinearCalibration(0.5,23.9,-30.0,30.0,0.5,"unit_of_meas");
        expResult = 26.1410;
        assertEquals("Realistic case",expResult,instance.applyCal(10.0),0.0001);
             
        
    }
    /**
     * Test of expected exception in constructor, of class AdcLinearCalibration.
     */
    @Test (expected=Exception.class)//(expected=IllegalArgumentException.class)//
    public void testApplyCalEx() {
        System.out.println("AdcLinearCalibration constructor");
        
        //double m = 1 / 0;//(1.0-1.0);
        //throw new java.lang.IllegalArgumentException("test");
        AdcLinearCalibration instance = new AdcLinearCalibration(1.0,1.0,5.0,3.0,0.0,"unit_of_meas");
       
    }
}
