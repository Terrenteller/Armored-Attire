package com.riintouge.armoredattire;

public enum OverrideCost
{
	// The cosmetic item will not be destroyed when applied.
	// It will not be returned when the override is removed.
	NO_CONSUME_NO_RETURN,

	// The cosmetic item will be destroyed when applied.
	// It will be returned when the override is removed.
	CONSUME_RETURN,

	// The cosmetic item will be destroyed when applied.
	// It will not be returned when the override is removed.
	CONSUME_NO_RETURN
}
