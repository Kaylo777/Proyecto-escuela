package com.example.administracion.repository;

import com.example.administracion.model.Docente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocenteRepository extends JpaRepository<Docente, Long> {
}