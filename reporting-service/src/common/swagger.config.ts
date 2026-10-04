import { INestApplication } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

export function configurarSwagger(app: INestApplication) {
  const config = new DocumentBuilder()
    .setTitle('TriageDesk reporting-service')
    .setDescription(
      'Proyecciones de tickets, resumen y cumplimiento de SLA. Eventos por red interna; consultas via gateway.',
    )
    .setVersion('0.1.0')
    .build();
  const documento = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('api-docs', app, documento, {
    jsonDocumentUrl: 'api-docs-json',
  });
  return documento;
}
