package com.example.alumnos.controller;

import com.example.alumnos.dto.AlumnoRequest;
import com.example.alumnos.dto.AlumnoResponse;
import com.example.alumnos.rabbit.AlumnoPublisher;
import com.example.alumnos.repository.AlumnoRepository;
import com.example.alumnos.service.AlumnoMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * CRUD de alumnos.
 *
 * <p><b>El controller no tiene seguridad ni reglas de negocio.</b> Eso es
 * intencional:</p>
 * <ul>
 *   <li>La autorizacion la aplica el gateway, antes de que la peticion llegue
 *       aca. Este servicio solo exige el token de servicio, que prueba que la
 *       peticion paso por el gateway.</li>
 *   <li>La conversion entre entidad y DTO la hace AlumnoMapper.</li>
 *   <li>Nunca se expone ni se acepta la entidad {@code Alumno}: se entra y se sale
 *       con DTOs, para que el esquema de la base de datos no se mezcle con el de
 *       la API.</li>
 * </ul>
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
    public List<AlumnoResponse> listar() {
        return AlumnoMapper.toResponseList(
                repository.findAll(Sort.by(Sort.Direction.ASC, "id")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlumnoResponse> obtener(@PathVariable Long id) {
        return repository.findById(id)
                .map(AlumnoMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlumnoResponse crear(@Valid @RequestBody AlumnoRequest request) {
        var guardado = repository.save(AlumnoMapper.toEntity(request));
        publisher.publicarAlumnoCreado(guardado);
        return AlumnoMapper.toResponse(guardado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlumnoResponse> actualizar(@PathVariable Long id,
                                                      @Valid @RequestBody AlumnoRequest request) {
        return repository.findById(id)
                .map(existente -> {
                    AlumnoMapper.actualizar(existente, request);
                    return AlumnoMapper.toResponse(repository.save(existente));
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}