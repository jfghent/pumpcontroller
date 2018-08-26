/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iotest2;
//comment just for testing commit
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
//import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.gpio.extension.ads.ADS1115GpioProvider;
import com.pi4j.gpio.extension.ads.ADS1115Pin;
import com.pi4j.gpio.extension.ads.ADS1x15GpioProvider;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
//import com.pi4j.io.gpio.GpioPinShutdown;
//import com.pi4j.io.gpio.GpioProvider;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.RaspiPin;
//import com.pi4j.io.gpio.PinMode;
//import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
//import com.pi4j.io.gpio.event.GpioPinAnalogValueChangeEvent;
//import com.pi4j.io.gpio.event.GpioPinListener;
//import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
//import com.pi4j.io.gpio.trigger.GpioTrigger;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
//import java.util.Collection;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.Timer;
//import java.util.concurrent.Callable;
//import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import iotest2.AdcLinearCalibration;
//import iotest2.CalibratedGpioPinAnalogInput;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;

/**
 *
 * @author Jon
 */
public class IoTest2 {



    
    /**
     * @param args the command line arguments
     * @throws java.lang.InterruptedException
     * @throws com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException
     * @throws java.io.IOException
     */


     
    IoTest2(){
        
    }   
    
    private static MqttClient mqttClient;
    private static ModeControl operMode;
    private static PumpStates.State curr_state;
    private static boolean full_charge_state_requested;
    
    private static final double MAX_SYSTEM_PRESSURE = 62.0;
    private static final double MIN_SYSTEM_PRESSURE = 42.0;
    private static final double CHARGED_SYSTEM_PRESSURE = 60.0;
    
    private static String MQTT_BROKER       = "tcp://192.168.1.46:1885";
    private static String MQTT_CLIENTID     = "pi-e595-pump";
    
    public static void main(String args[]) throws InterruptedException, I2CFactory.UnsupportedBusNumberException, IOException {
        //String classpathStr = System.getProperty("java.class.path");
	//System.out.print("classpathStr: " + classpathStr);
        
        Logger logger = LoggerFactory.getLogger(IoTest2.class);
        
        //number formats

        final DecimalFormat df = new DecimalFormat("#.##");
        final DecimalFormat pdf = new DecimalFormat("###.#");

        //System.out.println("************ Begining Execution ************");
        logger.info("Pump Controller Logger is online. - UID 20180826 15:05");
        //logger.info("Logger info level is online.");
        
        
        
        // Create EmailAlerter
        SimplestFileReader sfr = new SimplestFileReader();
        String pwd = sfr.get("/home/pi/prod/pwd.txt");
        
        EmailAlerter emailAlerter = new EmailAlerter("smtp.powerxmail.com",
                                                     "jonghent1@nxlink.com",
                                                     pwd);
        emailAlerter.addRecipient("jfghent@gmail.com");
        emailAlerter.addRecipient("benghent@gmail.com");
        
        emailAlerter.raiseAlert("Pump Controller Restart", "The pump controller is reporting a restart at " + getCurrentTimeStamp() + ".");
        
        // Create gpio controller
        final GpioController gpio = GpioFactory.getInstance();
        
        // Create pin to use as a makeshift status LED
        final GpioPinDigitalOutput statusLedPin =  gpio.provisionDigitalOutputPin(RaspiPin.GPIO_28);
        final statusIndicator statusLed = new statusIndicator(statusLedPin);
        
        // Create custom ADS1115 GPIO provider
        final ADS1115GpioProvider gpioProvider = new ADS1115GpioProvider(I2CBus.BUS_1, ADS1115GpioProvider.ADS1115_ADDRESS_0x48);
        
        // Provision and calibrate gpio analog input pins from ADS1115
        //  Well Pump Supply Current - Pin A0 on the ADS1115
        GpioPinAnalogInput adcWellPumpCurrent = gpio.provisionAnalogInputPin(gpioProvider, ADS1115Pin.INPUT_A0, "Well Pump Supply Current Sensor (ADS1115 0x48 A0)");
        gpioProvider.setProgrammableGainAmplifier(ADS1x15GpioProvider.ProgrammableGainAmplifierValue.PGA_6_144V, adcWellPumpCurrent.getPin());
        AdcLinearCalibration calWellPumpCurrent = new AdcLinearCalibration(0.0,5.0,0.0,30.0,0.0,"Amps");//TODO: Get actual Cal for this thing
        CalibratedGpioPinAnalogInput ampWellPumpCurrent = new CalibratedGpioPinAnalogInput(adcWellPumpCurrent,
                                                                                           gpioProvider.getProgrammableGainAmplifier(adcWellPumpCurrent.getPin()).getVoltage(),
                                                                                           ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE,calWellPumpCurrent);
        /************************************************
         * Configure the water pressure sensor settings
         */

        //  Define the error handler if the pressure changes too rapidly
        MeasurementFailure PressureMeasurementFailure = new MeasurementFailure(){
            @Override
            public void run(double valueDelta, long timeDelta){
                gpio.shutdown();
                curr_state = PumpStates.State.ERROR;
                publishState(curr_state.name());
                String error1 = "Rapid water pressure change detected. System shutdown.";
                String error2 = "  Change was measured as " + df.format(valueDelta) + "psi in " + df.format(timeDelta) + "seconds.";
                logger.error(error1);
                logger.error(error2);
                
                emailAlerter.raiseAlert("PUMP CONTROLLER ALERT", "This is an alert from the pump controller generated at " + getCurrentTimeStamp() + ".\r\n" + error1 + "\r\n" + error2);
                while(true); //TODO: More meaningful error handling, including notifying a human

            }
        };
        
        //  System Water Pressure - Pin A1 on the ADS1115
        GpioPinAnalogInput adcWaterPressure = gpio.provisionAnalogInputPin(gpioProvider, ADS1115Pin.INPUT_A1, "System Water Pressure Transducer (ADS1115 0x48 A1)");
        gpioProvider.setProgrammableGainAmplifier(ADS1x15GpioProvider.ProgrammableGainAmplifierValue.PGA_6_144V, adcWaterPressure.getPin());
        AdcLinearCalibration calWaterPressure = new AdcLinearCalibration(0.5,4.5,0.0,100.0,-12.5,"PSI");
        CalibratedGpioPinAnalogInputSafe psiWaterPressure = new CalibratedGpioPinAnalogInputSafe(adcWaterPressure,
                                                                                           gpioProvider.getProgrammableGainAmplifier(adcWaterPressure.getPin()).getVoltage(),
                                                                                           ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE,calWaterPressure,PressureMeasurementFailure,10.0);
        
        //CalibratedGpioPinAnalogInput psiWaterPressure = new CalibratedGpioPinAnalogInput(adcWaterPressure,
        //                                                                                   gpioProvider.getProgrammableGainAmplifier(adcWaterPressure.getPin()).getVoltage(),
        //                                                                                   ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE,calWaterPressure);
        
        
        
        long timerPumpStart = -1;
        long timeoutPumpStart = 3*1000; //three seconds
        long timerWellRest = -1;
        long timeoutWellRest = 2*60*1000; //two minutes
        long timeoutPumpRest = 30*1000; //30 seconds  //final GpioPinDigitalOutput pumpRelay =  gpio.provisionDigitalOutputPin(RaspiPin.GPIO_29);
        
        //pumpRelay.setState(PinState.HIGH);
        //pumpRelay.setShutdownOptions(true, PinState.HIGH);
        
        Pump pumpRelay = new Pump(gpio,RaspiPin.GPIO_29,timeoutPumpRest);
        
        operMode = new ModeControl();
        operMode.setEnableHighFlowTimeout(true);
        operMode.setHighFlowTimeoutValue(10*10*1000L);//Try one hour.
        
        //IrrigationTimer zone1 = new IrrigationTimer();
        //zone1.setRuntime(10*60*1000);
        //zone1.setGpioPin(RaspiPin.GPIO_21, gpio);

        
        //unused input: //gpio.provisionAnalogInputPin(gpioProvider, ADS1115Pin.INPUT_A2, "MyAnalogInput-A2"),
        //unused input: //gpio.provisionAnalogInputPin(gpioProvider, ADS1115Pin.INPUT_A3, "MyAnalogInput-A3"),
        
        curr_state = PumpStates.State.INIT;
        PumpStates.State prev_state = PumpStates.State.NONE;
        full_charge_state_requested = false;
        
        //zone1.start();
        //zone1.pause();
        statusLed.set(StatusLedValue.STATUS_LED_OFF);
        
            //String topic        = "home/irrigation/log";
            //String content      = "SYSTEM INITIALIZING";
            //int qos             = 2;

            //MemoryPersistence persistence = new MemoryPersistence();
        try
        {
                mqttCallback mqttRec = new mqttCallback();

                mqttClient = new MqttClient(MQTT_BROKER, MQTT_CLIENTID); //, persistence);
                mqttClient.setCallback(mqttRec);
                

                //logger.info("Publishing message");
                //MqttMessage message = new MqttMessage(content.getBytes());
                //message.setQos(qos);
                //mqttClient.publish(topic, message);
                //logger.info("Message published");
                
                //sampleClient.disconnect();
                //logger.info("Disconnected");
        }
        catch(Exception e){
            logger.info("Exception initializing the mqtt client connection: " +
                    e.toString());
        }
        mqttClientConnect();
        
        
        while (true){
            
            String Pressure = df.format(psiWaterPressure.getValue());
            String Current = df.format(ampWellPumpCurrent.getValue());
            
            mqttClientConnect();
            publishPressure(Pressure);
            publishCurrent(Current);
            
            logger.info("Curr State: " + curr_state.toString() + " - " + operMode.getModeString());
            logger.info("     Water Pressure : " + Pressure);
            logger.info("     Pump Current   : " + Current);
            logger.info("     Pump Switch    : " + pumpRelay.getOnOff());
            //logger.info("     Zone Running   : " + zone1.isRunning());
            //logger.info("     Zone Paused    : " + zone1.isPaused());
            //logger.info("     Zone Elapsed   : " + zone1.getElapsedTime());
            
            switch (curr_state) {
         
                case INIT:
                    //pump OFF
                    pumpRelay.turnOff();
                    
                    curr_state = PumpStates.State.FULLY_CHARGED;
                    
                    break;
                case FULLY_CHARGED:
                    //pump OFF
                    pumpRelay.turnOff();
                    
                    statusLed.set(StatusLedValue.STATUS_LED_ON);
                    full_charge_state_requested = false;
                    
                    //irrigation - no effect
                    if (psiWaterPressure.getValue() < CHARGED_SYSTEM_PRESSURE){
                        curr_state = PumpStates.State.PARTIALLY_CHARGED;
                    }/*else if(zone1.isRunning()){
                        zone1.resume();
                    }*/ else {
                        
                    }
                    
                    break;
                case PARTIALLY_CHARGED:
                    //pump OFF
                    pumpRelay.turnOff();
                    
                    statusLed.set(StatusLedValue.STATUS_LED_ON);
                    
                    //We want two set points for two different modes: "Normal" and "High Flow"
                    //Normal is for normal household operation and mimics the
                    //typical pressure switch found on most well installations.
                    //High Flow has a higher start rate and is used to keep the
                    //pressure in the system high even while drawing water. This
                    //is useful in irrigation, for example, to maximize water
                    //flow and keep system pressure as high as possible for as
                    //long as possible.
                    if (operMode.getMode() == Modes.NORMAL_OPERATION_MODE && psiWaterPressure.getValue() < MIN_SYSTEM_PRESSURE) 
                    {
                        curr_state = PumpStates.State.PUMP_START_INIT;
                    } else if(operMode.getMode() == Modes.HIGH_FLOW_MODE && psiWaterPressure.getValue() < CHARGED_SYSTEM_PRESSURE)
                    {
                        curr_state = PumpStates.State.PUMP_START_INIT;
                    } else if (full_charge_state_requested)
                    {
                        curr_state = PumpStates.State.PUMP_START_INIT;
                    }else 
                    {
                        
                    }
                    break;
                case PUMP_START_INIT:
                    //PUMP_START_INIT initializes the timeout for PUMP_START.
                    
                    /*********
                     * pumpRelay.turnOn() will return FALSE if the pump has been
                     * turned off recently. It will not be true again until the
                     * pump has had time to rest.
                     */
                    if(!pumpRelay.turnOn())//turn pump relay ON, returns false if the pump needs a break (per timeout) 
                    {
                        //PUMP_REST is used to poll the timeout and returns back to PUMP_START_INIT when the timeout has expired. 
                        curr_state = PumpStates.State.PUMP_REST;
                    } else
                    {
                        timerPumpStart = System.currentTimeMillis() + timeoutPumpStart; //initialize the timer to current time + timeout
                        curr_state = PumpStates.State.PUMP_START;
                    }
                    if(psiWaterPressure.getValue() > MAX_SYSTEM_PRESSURE)
                        curr_state = PumpStates.State.FULLY_CHARGED;
                    break;
                case PUMP_START:
                    //PUMP_START checks to make sure the current rises above
                    //the full pump threshold. This routine waits pumpStartTimeout
                    //milliseconds before reverting to a rest state. A low 
                    //current value may indicate a dry well or some other 
                    //electrical issue.
                    pumpRelay.turnOn(); //turn pump relay ON
                    
                    statusLed.set(StatusLedValue.STATUS_LED_FAST);
                    
                    if(System.currentTimeMillis() > timerPumpStart){ //have we exceeded our timeout?
                        curr_state = PumpStates.State.WELL_REST_START;
                    } //no, check the current next
                    if(ampWellPumpCurrent.getValue()>6.0){ //have we achieved a healthy current?
                        curr_state = PumpStates.State.PUMPING; //yes, go to PUMPING
                    } //no, continue counting
                        
                    break;
                case PUMPING:
                    //pump ON
                    //pumpRelay.turnOn();
                    
                    statusLed.set(StatusLedValue.STATUS_LED_FAST);
                    
                    if (psiWaterPressure.getValue() > MAX_SYSTEM_PRESSURE) //keep going until max system pressure is reached
                    {
                        curr_state = PumpStates.State.FULLY_CHARGED;
                    }else
                    { //check if we are still pumping water by validating a healthy current
                       double well_current = ampWellPumpCurrent.getValue();
                       if(well_current < 6.1 ){ //If we're pumping air, go to rest state
                           curr_state = PumpStates.State.WELL_REST_START;
                       } else
                       {
                           //keep going
                       }
                    }
                    
                    break;
                case WELL_REST_START:
                    //pump off
                    pumpRelay.turnOff();
                    
                    timerWellRest = System.currentTimeMillis() + timeoutWellRest;
                    
                    //irrigation pause
                    //zone1.pause();
                    
                    curr_state = PumpStates.State.WELL_REST;
                    
                    break;
                case WELL_REST:
                    //pump off
                    pumpRelay.turnOff();
                    
                    statusLed.set(StatusLedValue.STATUS_LED_SLOW);
                    
                    //irrigation pause
                    //zone1.pause();
                    
                    if(System.currentTimeMillis() > timerWellRest ){
                        curr_state = PumpStates.State.INIT;
                    }else {
                        
                    }
                    
                    break;
                case PUMP_REST:
                    if(!pumpRelay.resting())
                        curr_state = PumpStates.State.PUMP_START_INIT;
                    break; 
                default: 
                    break;
            }
            
          
            //Only publish our state to the broker if it has changed.
            if(curr_state!=prev_state)
            {
                publishState(curr_state.name());
            }
            prev_state = curr_state;
            
            //tick time
            Thread.sleep(1000);
        }
        
    }
   
    /*************
     * 
     *  class Pump
     * 
     * Assumption: The pump can run forever if it has a steady supply of water.
     * However, the pump needs time to cool down each time it is turned off. 
     * This is managed inside the Pump class with a private timer defined by
     * the user at creation.
     * 
     */
    
    private static class Pump {
        private GpioController gpio;
        private GpioPinDigitalOutput relay_pin;
        private Long last_time_turned_off;
        private Long pump_rest_time_ms = 30*1000L; //default to a safe value of 30 seconds
        private boolean pump_on;
        
        Pump(GpioController gpio,Pin pin,long pump_rest_time_ms){
            this.gpio = gpio;
            this.relay_pin =  gpio.provisionDigitalOutputPin(pin); //RaspiPin.GPIO_29);
            this.relay_pin.setShutdownOptions(true, PinState.HIGH);
            this.last_time_turned_off = System.currentTimeMillis();
            this.pump_rest_time_ms = pump_rest_time_ms;
            this.pump_on = false;
            turnOff();
        }
        
        //Do not turn on the pump if it hasn't had enough time to cool down, as 
        //indicated by pump_rest_time_ms. Instead, return false.
        public boolean turnOn(){
            if(this.pump_rest_time_ms < System.currentTimeMillis() - this.last_time_turned_off)
            {
                this.relay_pin.setState(PinState.LOW);
                this.pump_on = true;
                return true;
            } else
            {
                return false;
            }
        }
        
        public void turnOff(){
            this.relay_pin.setState(PinState.HIGH);
            if(pump_on)
            {
                this.last_time_turned_off = System.currentTimeMillis();
            } 
            this.pump_on = false;
        }
        
        public boolean resting(){
            Logger logger = LoggerFactory.getLogger(IoTest2.class);
            long ms = System.currentTimeMillis();
            logger.info(" *** Elapsed Time: " + (ms - this.last_time_turned_off));// - this.pump_rest_time_ms));
            Boolean what = this.pump_rest_time_ms < (System.currentTimeMillis() - this.last_time_turned_off);
            return !what;
        }
        
        public PinState getState(){
            return this.relay_pin.getState();
        }
        
        public String getOnOff(){
            if(this.relay_pin.getState()==PinState.HIGH) return "Off";
            if(this.relay_pin.getState()==PinState.LOW) return "On";
            return "Unexpected value in getOnOff: " + this.relay_pin.getState().toString();
        }
        
    }
    
    public static class MeasurementFailure implements SafeAdcInputMeasurementFailure
    {
        @Override
        public void run(double valueDelta, long timeDelta) {
            //do error stuff TODO:
        }
            
    };
    
    /***********************
     * 
     * void setStatusLed(long rate, GpioPinDigitalOutput pin);
     * 
     * rate:
     *   -1 - turn LED continuous off
     *   0 - turn LED continuous on
     *   >0 - blink LED at rate specified in milliseconds
     *   All other values are ignored.
     * 
     * pin:
     *    The GpioPinDigitalOutput being used as the status indicator
     * 
     * 
     * NOTE: This code is based on the LED being sourced by the output pin. This
     * means that if the pin is high then the light is on. If, instead, the LED
     * is drained by the output pin, meaning when the pin is low the light is
     * on, the code needs to be changed in three places: the construtor, the
     * STATUS_LED_OFF condition, and the STATUS_LED_ON condition.
     * 
     */
    static class statusIndicator {
        
        private GpioPinDigitalOutput p;
        
        statusIndicator(GpioPinDigitalOutput pin){
            this.p = pin;
            this.p.setShutdownOptions(true, PinState.LOW); //turn indicator LED OFF if a gpio.shutdown is issued()
        }
        
        public void set(long rate){
            
            p.blink(0); //should turn off Future<> and reset all timers per the source code for pi4j: https://github.com/Pi4J/pi4j/blob/master/pi4j-core/src/main/java/com/pi4j/io/gpio/impl/GpioScheduledExecutorImpl.java
        
            if (rate == StatusLedValue.STATUS_LED_OFF){ //turns the light solidly off
                p.setState(PinState.LOW);
            } else if (rate == StatusLedValue.STATUS_LED_ON){ //turns the light solidly on
                p.setState(PinState.HIGH);
            } else if (rate > 0){ //sets the blink rate appropriately
                p.blink(rate);
            } else { //do nothing if not -1, 0, or >0

            }
        }
        
    }
    
    /***********************
     * 
     *  void publishTopic(String topic, String payload);
     * 
     *  topic: mqtt topic 
     *  payload: mqtt payload 
     * 
     */
    private static void publishTopic(String topic, String payload){
        Logger logger = LoggerFactory.getLogger(IoTest2.class);
        try
        {
            mqttClientConnect();
            mqttClient.publish(topic,payload.getBytes(),2,true);
        } catch(Exception e)
        {
            logger.info("Exception caught publishing topic: "+ topic);
            logger.info("Exception: " + e.toString() );
        }
    }
    /***********************
     * 
     *  void publishAlert(String payload);
     * 
     *  payload: mqtt payload to publish to the home/alert topic
     * 
     */
    private static void publishAlert(String payload) {
        publishTopic("home/alert",payload);        
    }
    /***********************
     * 
     *  void publishState(String payload);
     * 
     *  payload: mqtt payload to publish to the home/irrigation/state topic
     * 
     */
    private static void publishState(String payload) {
        publishTopic("home/irrigation/state",payload);      
    }
    
    /***********************
     * 
     *  void publishCurrent(String payload);
     * 
     *  payload: mqtt payload to publish to the home/irrigation/pump_current topic
     * 
     */
    private static void publishCurrent(String payload) {
       publishTopic("home/irrigation/pump_current",payload); 
    }
    /***********************
     * 
     *  void publishPressure(String payload);
     * 
     *  payload: mqtt payload to publish to the home/irrigation/pressure topic
     * 
     */
    private static void publishPressure(String payload) {
        publishTopic("home/irrigation/pressure",payload); 
    }
    /***********************
     * 
     *  void publishMode(String payload);
     * 
     *  payload: mqtt payload to publish to the home/irrigation/mode topic
     * 
     */
    private static void publishMode(String payload) {
        publishTopic("home/irrigation/mode",payload);             
    }
    
    private static class ModeControl {
        private Modes curr_mode = Modes.NORMAL_OPERATION_MODE;
        private boolean high_flow_timeout_enabled = true;
        private Long high_flow_timeout = 10*60*1000L;//set at 10 minutes to start
        private Long time_of_high_flow_start = 0L;
        
        public void setEnableHighFlowTimeout(boolean b){
            this.high_flow_timeout_enabled = b;
        }
        public boolean getEnableHighFlowTimeout(){
            return this.high_flow_timeout_enabled;
        }
        
        public void setHighFlowTimeoutValue(Long t){
            this.high_flow_timeout = t;
        }
        public Long getHighFlowTimeoutValue(){
            return this.high_flow_timeout;
        }
        public Long getHighFlowTimeoutRemaining(){
            if(this.high_flow_timeout_enabled)
                return this.high_flow_timeout - System.currentTimeMillis() - this.time_of_high_flow_start;
            else
                return -1L; //indicates timeout not enabled
        }
        
        public boolean setMode(Modes m){
            if(m==Modes.HIGH_FLOW_MODE)
            {
                if(this.high_flow_timeout_enabled && this.high_flow_timeout > 0)
                {
                    //curr_mode = m;
                    this.time_of_high_flow_start = System.currentTimeMillis();
                    //return true;
                } else
                {
                    return false; //indicates an error: 
                }
            } 
            curr_mode = m;
            publishMode(curr_mode.name());
            return true;
        }
        
        public Modes getMode(){
            if(curr_mode==Modes.HIGH_FLOW_MODE)
            {
                if(getHighFlowTimeoutRemaining()<=0)
                {
                    setMode(Modes.NORMAL_OPERATION_MODE);
                }
            } 
            return curr_mode;
        }
        
        public String getModeString(){
            return curr_mode.name();
        }
            
    }
    /********************
     * mqttClientConnect 
     * 
     */
    
    
    private static void mqttClientConnect() {
        Logger logger = LoggerFactory.getLogger(IoTest2.class);
        
        if(mqttClient.isConnected()) return;
        
        try{
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            logger.info("Connecting to broker: "+ MQTT_BROKER);
            mqttClient.connect(connOpts);
            logger.info("Connected");
            logger.info("Subscribing to topic");

            //mqttClient.subscribe(topic);
            mqttClient.subscribe("home/irrigation/full_charge_request");
            mqttClient.subscribe("home/irrigation/mode_request");
            //mqttClient.subscribe("home/irrigation/state");
            logger.info("Subscribed to topic");
        } catch (Exception e){
            logger.info("Error connecting to MQTT Client: " + e.toString());
        }
    }
    /********************
     * mqttCallback implements MqttCallback with the methods:
     *    connectionLost
     *      Not implemented
     * 
     *    messageArrived
     *      Handles all subscribed traffic
     * 
     *    deliveryComplete
     *      Not implemented
     * 
     * 
     */
    static class mqttCallback implements MqttCallback {
        Logger logger = LoggerFactory.getLogger(IoTest2.class);
        
        @Override
        public void connectionLost(Throwable thrwbl) {
            mqttClientConnect();
            //TODO: implement reconnect logic - jg 201808180645
            //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void messageArrived(String string, MqttMessage mm) throws Exception {
            logger.info("MQTT Message Received:");
            logger.info("  string  : " + string); //topic
            logger.info("  message : " + mm.toString()); //payload
            
            String [] arrOfStr;
            
            switch(string){
                case("home/irrigation"):
                    
                    arrOfStr = string.split(",", 1); //1,On
 
                    for (String a : arrOfStr)
                        System.out.println(a);
                    
                    break;
                case("home/irrigation/full_charge_request"):
                    full_charge_state_requested = true;
                    break;
                /**************
                 * home/irrigation/mode_request
                 *  
                 * Payload:
                 * 
                 *      Basic Mode Request - the name of the mode, e.g. 
                 *          "HIGH_FLOW_MODE" or "NORMAL_MODE"
                 * 
                 *      Mode Request with Timeout - requesting HIGH_FLOW_MODE
                 *          carries the optional ability to set (or clear) a 
                 *          timeout, e.g. "HIGH_FLOW_MODE,60000". The timeout
                 *          value is in milliseconds. Setting a timeout <= 0
                 *          will disable the timeout.
                 * 
                 */
                case("home/irrigation/mode_request"):
                    arrOfStr = mm.toString().split(",", 1); //HIGH_FLOW_MODE,60000
                    try{
                        for(Modes a : Modes.values())

                            if(arrOfStr[0].equals(a.name()))
                            {
                                if(a==Modes.HIGH_FLOW_MODE)
                                {
                                    if(arrOfStr.length>1)
                                    {
                                        Long t = Long.parseLong(arrOfStr[1]);
                                        if(t<=0)
                                            operMode.high_flow_timeout_enabled=false;
                                        else
                                        {
                                            operMode.high_flow_timeout_enabled=true;
                                            operMode.setHighFlowTimeoutValue(t);
                                        }
                                    }

                                }

                                operMode.setMode(a);
                            } else
                                logger.info("Payload did not match a valid Modes value on receipt of topic home/irrigation/mode_request.");
                    } catch(Exception e)
                    {
                        logger.info("Error on home/irrigation/mode_request message receive in messageArrived: " + e.toString());
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken imdt) {
            //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
//testing 123
        
    
        
    }
    public static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }
 
    
    static class StatusLedValue{
            public static final long STATUS_LED_ON = 0;
            public static final long STATUS_LED_OFF = -1;
            public static final long STATUS_LED_FAST = 500;
            public static final long STATUS_LED_MED = 1000;
            public static final long STATUS_LED_SLOW = 2000;
    }
    
    static enum Modes {
        NORMAL_OPERATION_MODE, HIGH_FLOW_MODE
    }
}



