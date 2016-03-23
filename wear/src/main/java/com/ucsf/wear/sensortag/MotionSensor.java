package com.ucsf.wear.sensortag;

import com.ucsf.wear.services.SensorTagMonitoring;

import java.util.UUID;

/**
 * Implements the Motion Sensor.
 */
public class MotionSensor extends Sensor
{

    private Point3D accelerometer;
    private Point3D gyroscope;
    private Point3D magnetometer;

    public MotionSensor(UUID serviceUuid,SensorTagMonitoring mBluetoothLeService, String address)
    {
        super(serviceUuid,mBluetoothLeService,address);
        this.accelerometer=new Point3D(0.0,0.0,0.0);
        this.gyroscope=new Point3D(0.0,0.0,0.0);
        this.magnetometer=new Point3D(0.0,0.0,0.0);
        this.setSensorType(SensorTagConfiguration.SensorType.MOTION);
    }

    @Override
    public Point3D convert(byte[] value)
    {
        this.accelerometer=convertAccelerator(value);
        this.gyroscope=convertGyroscope(value);
        this.magnetometer=convertMagnetometer(value);
        if(this.measure==1)return this.accelerometer;
        else if(this.measure==2)return this.gyroscope;
        else return this.magnetometer;
    }

    private Point3D convertAccelerator(byte[] value)
    {
        // Range 8G
        final float SCALE = (float) 4096.0;
        int x = (value[7]<<8) + value[6];
        int y = (value[9]<<8) + value[8];
        int z = (value[11]<<8) + value[10];
        return new Point3D(((x / SCALE) * -1), y / SCALE, ((z / SCALE)*-1));
    }

    private Point3D convertGyroscope(byte[] value)
    {
        final float SCALE = (float) 128.0;
        int x = (value[1]<<8) + value[0];
        int y = (value[3]<<8) + value[2];
        int z = (value[5]<<8) + value[4];
        return new Point3D(x / SCALE, y / SCALE, z / SCALE);
    }

    private Point3D convertMagnetometer(byte[] value)
    {
        final float SCALE = (float) (32768 / 4912);
        if (value.length >= 18) {
            int x = (value[13]<<8) + value[12];
            int y = (value[15]<<8) + value[14];
            int z = (value[17]<<8) + value[16];
            return new Point3D(x / SCALE, y / SCALE, z / SCALE);
        }
        else return new Point3D(0,0,0);
    }

    @Override
    public String toString()
    {
        /*
        return "Accelerometer="+this.accelerometer.toString()+" G\n"
                +"Gyroscope="+this.gyroscope.toString()+" uT\n"
                +"Magnetometer="+this.magnetometer.toString()+" deg/s";
                */

        return "Accelerometer(G),"+this.accelerometer.toString()+","
                +"Gyroscope(deg/s),"+this.gyroscope.toString()+","
                +"Magnetometer(uT),"+this.magnetometer.toString()+",";
    }

}
