package com.projet.sigrh;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.projet.sigrh.repository.EmployeRepository;

@SpringBootTest
class SigrhApplicationTests {

	@Autowired
	private EmployeRepository employeRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void chargeLesEvaluateursAvecLeurPoste() {
		assertDoesNotThrow(employeRepository::findEvaluateursAvecPoste);
	}

}
