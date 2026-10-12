import { INestApplication } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

export function configurarSwagger(app: INestApplication) {
  const config = new DocumentBuilder()
    .setTitle('UrgentIA reporting-service')
    .setDescription(
      'Proyecciones de tickets, resumen y cumplimiento de SLA. Eventos por red interna; consultas via gateway.',
    )
    .setVersion('0.1.0')
    .addServer(
      'http://localhost:8080',
      'Gateway: consultas con JWT AGENTE/ADMIN',
    )
    .addServer('http://localhost:8085', 'Desarrollo local de P6')
    .addBearerAuth(
      { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' },
      'bearer',
    )
    .build();
  const documento = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('api-docs', app, documento, {
    jsonDocumentUrl: 'api-docs-json',
  });
  return documento;
}
