package org.example.task2.model;

import java.util.List;

public record Event(List<Address> recipients, Payload payload) {}