package org.example.task2;

import org.example.task2.model.Address;
import org.example.task2.model.Event;
import org.example.task2.model.Payload;
import org.example.task2.model.Result;

public interface Client {
    //блокирующий метод для чтения данных
    Event readData();

    //блокирующий метод отправки данных
    Result sendData(Address dest, Payload payload);
}