package com.kammoun.raven.library.redis.event;

/**
 * Pure signal — fired after /setspawn or /setafk writes a new row, telling every
 * other server's location cache to reload from MySQL. Carries no payload beyond
 * the inherited sourceServer/timestamp; the cheapest correct response is a full reload.
 */
public class LocationsUpdatedEvent extends CrossServerEvent {
}
