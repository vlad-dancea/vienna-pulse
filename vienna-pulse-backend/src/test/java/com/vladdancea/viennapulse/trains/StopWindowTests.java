package com.vladdancea.viennapulse.trains;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.vladdancea.viennapulse.trains.Trains.Stop;
import org.junit.jupiter.api.Test;

class StopWindowTests {

	// A trip: A dep 100, B arr 200 dep 230, C arr 300 dep 300, D arr 400.
	private static final List<Stop> STOPS = List.of(new Stop("A", "A", 100, 100, 0), new Stop("B", "B", 200, 230, 1),
			new Stop("C", "C", 300, 300, 2), new Stop("D", "D", 400, 400, 3));

	@Test
	void keepsTheSegmentsAroundTheWindow() {
		assertThat(TrainService.around(STOPS, 240, 260)).extracting(Stop::stopId).containsExactly("B", "C");
		assertThat(TrainService.around(STOPS, 210, 310)).extracting(Stop::stopId).containsExactly("A", "B", "C", "D");
	}

	@Test
	void startsAtTheFirstStopBeforeDeparture() {
		assertThat(TrainService.around(STOPS, 50, 150)).extracting(Stop::stopId).containsExactly("A", "B");
	}

	@Test
	void endsAtTheLastStopWhenTheWindowPassesTheEnd() {
		assertThat(TrainService.around(STOPS, 350, 900)).extracting(Stop::stopId).containsExactly("C", "D");
	}

}
