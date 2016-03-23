package com.ucsf.wear.sensortag;

import android.bluetooth.BluetoothDevice;

/**
 * Implements a special Pair class.
 */
public class Pair implements Comparable
{

    private BluetoothDevice key;
    private Integer value;

    /**
     * Basic constructor.
     */
    public Pair(BluetoothDevice key,Integer value)
    {
        this.key=key;
        this.value=value;
    }

    public Integer getValue()
    {
        return value;
    }

    public BluetoothDevice getKey() {return key;}

    public void setValue(Integer val) {
        this.value = val;
    }


    /**
     * compareTo.
     */
    @Override
    public int compareTo(Object o)
    {
        //return this.getValue().compareTo(((Pair)o).getValue());
        return ((Pair)o).getValue().compareTo(this.getValue());
    }

    /**
     * toString.
     */
    @Override
    //TODO: check if it's okay to modify this method
    public String toString()
    {
        return this.key.getAddress()+" "+this.value.toString();
    }

    /**
     * equals
     */
    @Override
    public boolean equals(Object o)
    {
        return this.key.getAddress().equals(((Pair) o).getKey().getAddress());
    }
}
