package com.example.alumnos.controller;

import com.example.alumnos.model.Alumno;
import com.example.alumnos.rabbit.AlumnoPublisher;
import com.example.alumnos.repository.AlumnoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD de alumnos. El token JWT llega validado desde el gateway y tambien se
 * valida aqui (resource server).
 *
 * <p>Los @PreAuthorize son el segundo nivel de defensa: aunque las reglas de
 * URL de SecurityConfig se relajasen por error, el metodo seguiria protegido.</p>
 */
@RestController
@RequestMapping("/api/alumnos")
public class AlumnoController {

    private final AlumnoRepository repository;
    private final AlumnoPublisher publisher;

    public AlumnoController(AlumnoRepository repository, AlumnoPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @GetMapping
    public List<Alumno> listar() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Alumno> obtener(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Alumno crear(@RequestBody Alumno alumno) {
        Alumno guardado = repository.save(alumno);
        publisher.publicarAlumnoCreado(guardado);
        return guardado;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Alumno> actualizar(@PathVariable Long id, @RequestBody Alumno datos) {
        return repository.findById(id).map(existente -> {
            existente.setNombre(datos.getNombre());
            existente.setApellido(datos.getApellido());
            existente.setDni(datos.getDni());
            existente.setEmail(datos.getEmail());
            existente.setCurso(datos.getCurso());
            existente.setFechaNacimiento(datos.getFechaNacimiento());
            return ResponseEntity.ok(repository.save(existente));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}