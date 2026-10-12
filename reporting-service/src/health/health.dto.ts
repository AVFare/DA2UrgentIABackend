import { ApiProperty } from '@nestjs/swagger';
export class HealthDto {
  @ApiProperty({ enum: ['UP'] }) status!: string;
  @ApiProperty({ enum: ['reporting-service'] }) service!: string;
  @ApiProperty({ enum: ['0.1.0'] }) version!: string;
}
