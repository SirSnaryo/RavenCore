package com.kammoun.raven.library.redis.event;

/**
 * Pure signal — fired after a preset is created, edited, deleted or toggled on any
 * server, telling every other server's Billford cache to reload the preset set from
 * MySQL. Carries no payload beyond the inherited sourceServer/timestamp; the cheapest
 * correct response is a full reload.
 */
public class BillfordPresetsUpdatedEvent extends CrossServerEvent {
}
