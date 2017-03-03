package com.qwildz.carcamera;

import jaron.simpleserialization.SerializationData;
import jaron.simpleserialization.SerializationInputStream;
import jaron.simpleserialization.SerializationOutputStream;
import jaron.simpleserialization.SerializationTypes;
import timber.log.Timber;

public class CommandData extends SerializationData {

    int leftMotor = 0;
    boolean leftDirection = true;
    int rightMotor = 0;
    boolean rightDirection = true;
    int yawServo = 90;

    @Override
    public void readData(SerializationInputStream input) {
        leftMotor = input.readInteger();
        leftDirection = input.readBoolean();
        rightMotor = input.readInteger();
        rightDirection = input.readBoolean();
        yawServo = input.readInteger();
    }

    @Override
    public void writeData(SerializationOutputStream output) {
        output.writeInteger(leftMotor);
        output.writeBoolean(leftDirection);
        output.writeInteger(rightMotor);
        output.writeBoolean(rightDirection);
        output.writeInteger(yawServo);
    }

    @Override
    public int getDataSize() {
        int dataSize = 0;
        dataSize += SerializationTypes.SIZEOF_INTEGER;
        dataSize += SerializationTypes.SIZEOF_BOOLEAN;
        dataSize += SerializationTypes.SIZEOF_INTEGER;
        dataSize += SerializationTypes.SIZEOF_BOOLEAN;
        dataSize += SerializationTypes.SIZEOF_INTEGER;
        return dataSize;
    }

    CommandData assign(CommandData other) {
        this.leftMotor = other.leftMotor;
        this.leftDirection = other.leftDirection;
        this.rightMotor = other.rightMotor;
        this.rightDirection = other.rightDirection;
        this.yawServo = other.yawServo;

        return this;
    }

    public boolean equals(Object o) {
        if(o == null) return false;

        CommandData data = (CommandData) o;

//        Timber.d("O = " +
//                data.leftMotor + " " + data.leftDirection + " " +
//                data.rightMotor + " " + data.rightDirection);
//
//        Timber.d("N = " +
//                this.leftMotor + " " + this.leftDirection + " " +
//                this.rightMotor + " " + this.rightDirection);

        return (data.leftMotor == this.leftMotor
                && data.leftDirection == this.leftDirection
                && data.rightMotor == this.rightMotor
                && data.rightDirection == this.rightDirection
                && data.yawServo == this.yawServo);
    }
}

