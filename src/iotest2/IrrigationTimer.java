/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iotest2;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
//import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jon
 */
public class IrrigationTimer {
    
    private boolean running = false;
    private boolean paused = false;
    private long start_time;
    private long run_time = 0;
    private long elapsed_time;
    private GpioPinDigitalOutput gpio_pin; // =  gpio.provisionDigitalOutputPin(RaspiPin.GPIO_21); //Rpi header pin 29
    private boolean pin_configured = false;
    //private String tick_state = "init";
    
    Logger logger = LoggerFactory.getLogger(IoTest2.class);
    
    public boolean isRunning(){
        return this.running;
    }
    
    public boolean isPaused(){
        return this.paused;
    }
    
    public boolean isConfigured(){
        return pin_configured && (run_time > 0);
    }
    
    public long getElapsedTime(){
        return this.elapsed_time;
    }
    
    public void setRuntime(long ms){
        this.run_time = ms;
    }
    
    public void setGpioPin(Pin pin, GpioController gpio){
        this.gpio_pin =  gpio.provisionDigitalOutputPin(pin); 
        this.gpio_pin.setShutdownOptions(true, PinState.HIGH);
        this.pin_configured = true;
    }
    
    public void pause(){
        if (!this.isRunning()) return;
        if (this.isPaused()) return;
        logger.info(" Zone 1 : Pause");
        this.paused = true;
        //turn off GPIO
        this.gpio_pin.setState(PinState.HIGH);
        //stop timer
        this.elapsed_time += System.currentTimeMillis() - this.start_time;
        //this.start_time = System.currentTimeMillis();
    }
    
    public void resume(){
        if (!this.isRunning()) return;
        if (!this.isPaused()) return;
        logger.info(" Zone 1 : Resume");
        this.paused = false;
        //turn on GPIO
        this.gpio_pin.setState(PinState.LOW);
        //start timer
        //this.elapsed_time += System.currentTimeMillis() - this.start_time;
        this.start_time = System.currentTimeMillis();
    }
    
    public void start(){
        logger.info(" Zone 1 : Start");
        this.running = true;
        this.paused = false;
        //initialize timer
        this.elapsed_time = 0;
        this.start_time = System.currentTimeMillis();
        //turn on GPIO
        this.gpio_pin.setState(PinState.LOW);
    }
    
    public void stop(){
        logger.info(" Zone 1 : Stop");
        //turn off GPIO
        this.gpio_pin.setState(PinState.HIGH);
        this.elapsed_time += System.currentTimeMillis() - this.start_time;
        this.start_time = 0;
        this.running = false;
        this.paused = false;
    }
    
    public void tick(){
        if(this.isRunning() && !this.isPaused())
        {
            this.elapsed_time += System.currentTimeMillis() - this.start_time;
            this.start_time = System.currentTimeMillis();
            if(this.elapsed_time > this.run_time){
                this.stop();
            }
        }
    }
    
}
