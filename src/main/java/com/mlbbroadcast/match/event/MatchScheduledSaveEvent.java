package com.mlbbroadcast.match.event;

import com.mlbbroadcast.match.entities.Matches;

public record MatchScheduledSaveEvent(Matches match) {
}
