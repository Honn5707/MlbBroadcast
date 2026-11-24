package com.mlbbroadcast.bet;

//PENDING -> WON/LOST/CANCELED/NONE (정산), PENDING -> ERROR (락 획득 실패), PENDING/ERROR -> FAILED (보정 4회 실패)
public enum BetStatus { PENDING, WON, LOST, CANCELED, NONE, ERROR, FAILED }
